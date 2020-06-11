/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange

import com.d3.commons.util.getRandomString
import com.d3.commons.util.irohaEscape
import com.d3.commons.util.toHexString
import com.d3.exchange.util.ExchangerServiceTestEnvironment
import integration.helper.D3_DOMAIN
import integration.helper.IrohaConfigHelper
import integration.helper.IrohaIntegrationHelperUtil
import integration.registration.RegistrationServiceTestEnvironment
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TRANSFER_WAIT_TIME = 7_500L

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangerIntegrationTest {

    private val gson = GsonInstance.get()

    private val integrationHelper = IrohaIntegrationHelperUtil()

    private val registrationServiceEnvironment = RegistrationServiceTestEnvironment(integrationHelper)

    private val exchangerServiceEnvironment = ExchangerServiceTestEnvironment(integrationHelper)

    init {
        exchangerServiceEnvironment.init()
        registrationServiceEnvironment.registrationInitialization.init()

        runBlocking { delay(10_000) }
    }

    @AfterAll
    fun closeEnvironments() {
        exchangerServiceEnvironment.close()
        registrationServiceEnvironment.close()
    }

    private val timeoutDuration = Duration.ofMinutes(IrohaConfigHelper.timeoutMinutes)

    /**
     * Test of a correct asset exchange
     * @given Registered user in Iroha
     * @when User sends transfer to an exchange service account
     * @then User gets incoming transaction containing converted asset from the service
     */
    @Test
    fun correctExchange() {
        Assertions.assertTimeoutPreemptively(timeoutDuration) {
            val tokenA = integrationHelper.createAsset().get()
            val tokenB = integrationHelper.createAsset().get()

            val userName = String.getRandomString(7)
            val userKeypair = Ed25519Sha3().generateKeypair()
            val userPubkey = userKeypair.public.toHexString()
            val res = registrationServiceEnvironment.registerV1(userName, userPubkey)
            assertEquals(200, res.statusCode)
            val userId = "$userName@$D3_DOMAIN"

            integrationHelper.addIrohaAssetTo(
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                "10"
            )
            integrationHelper.addIrohaAssetTo(
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenB,
                "10"
            )

            integrationHelper.addIrohaAssetTo(userId, tokenA, "1")
            integrationHelper.transferAssetIrohaFromClient(
                userId,
                userKeypair,
                userId,
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                tokenB,
                "1"
            )

            Thread.sleep(TRANSFER_WAIT_TIME)

            val etherBalance = integrationHelper.getIrohaAccountBalance(userId, tokenB)
            assertTrue(BigDecimal(etherBalance) > BigDecimal.ZERO)
        }
    }

    /**
     * Test of a correct asset exchange
     * @given Registered user in Iroha
     * @when User sends transfer to an exchange service account
     * @then User gets incoming transaction containing converted asset from the service
     */
    @Test
    fun correctDcExchange() {
        Assertions.assertTimeoutPreemptively(timeoutDuration) {
            val tokenA = "xor#sora"
            val tokenB = "usd#xst"

            val userName = String.getRandomString(7)
            val userKeypair = Ed25519Sha3().generateKeypair()
            val userPubkey = userKeypair.public.toHexString()
            val res = registrationServiceEnvironment.registerV1(userName, userPubkey)
            assertEquals(200, res.statusCode)
            val userId = "$userName@$D3_DOMAIN"

            integrationHelper.addIrohaAssetTo(userId, tokenA, "10")
            integrationHelper.transferAssetIrohaFromClient(
                userId,
                userKeypair,
                userId,
                exchangerServiceEnvironment.fiatExchangerAccountId,
                tokenA,
                tokenB,
                "10"
            )

            Thread.sleep(TRANSFER_WAIT_TIME)

            val newBalance = integrationHelper.getIrohaAccountBalance(userId, tokenB)
            assertTrue(BigDecimal(newBalance) > BigDecimal.ZERO)
        }
    }

    /**
     * Test of a incorrect asset exchange
     * @given Registered user in Iroha
     * @when User sends transfer to an exchange service account specifying unknown target asset
     * @then User gets rollback transaction
     */
    @Test
    fun rollbackUnknownExchange() {
        Assertions.assertTimeoutPreemptively(timeoutDuration) {
            val tokenA = integrationHelper.createAsset().get()
            val tokenB = "soramichka#sora"

            val userName = String.getRandomString(7)
            val userKeypair = Ed25519Sha3().generateKeypair()
            val userPubkey = userKeypair.public.toHexString()
            val res = registrationServiceEnvironment.registerV1(userName, userPubkey)
            assertEquals(200, res.statusCode)
            val userId = "$userName@$D3_DOMAIN"

            integrationHelper.addIrohaAssetTo(
                userId,
                tokenA,
                "1"
            )
            integrationHelper.transferAssetIrohaFromClient(
                userId,
                userKeypair,
                userId,
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                tokenB,
                "1"
            )

            Thread.sleep(TRANSFER_WAIT_TIME)

            val xorBalance = integrationHelper.getIrohaAccountBalance(userId, tokenA)
            assertEquals("1", xorBalance)
        }
    }

    /**
     * Test of a incorrect asset exchange
     * @given Registered user in Iroha
     * @when User sends transfer to an exchange service account specifying much more volume than can be expected
     * @then User gets rollback transaction
     */
    @Test
    fun rollbackTooMuchExchange() {
        Assertions.assertTimeoutPreemptively(timeoutDuration) {
            val tokenA = integrationHelper.createAsset().get()
            val tokenB = integrationHelper.createAsset().get()

            val userName = String.getRandomString(7)
            val userKeypair = Ed25519Sha3().generateKeypair()
            val userPubkey = userKeypair.public.toHexString()
            val res = registrationServiceEnvironment.registerV1(userName, userPubkey)
            assertEquals(200, res.statusCode)
            val userId = "$userName@$D3_DOMAIN"
            val tooMuchAmount = "1000000"

            integrationHelper.addIrohaAssetTo(
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                "10"
            )
            integrationHelper.addIrohaAssetTo(
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenB,
                "10"
            )

            integrationHelper.addIrohaAssetTo(
                userId,
                tokenA,
                tooMuchAmount
            )
            integrationHelper.transferAssetIrohaFromClient(
                userId,
                userKeypair,
                userId,
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                tokenB,
                tooMuchAmount
            )

            Thread.sleep(TRANSFER_WAIT_TIME)

            val xorBalance = integrationHelper.getIrohaAccountBalance(userId, tokenA)
            assertEquals(tooMuchAmount, xorBalance)
            val etherBalance = integrationHelper.getIrohaAccountBalance(userId, tokenB)
            assertEquals("0", etherBalance)
        }
    }

    /**
     * Test of a incorrect asset exchange
     * @given Registered user in Iroha
     * @when User sends transfer to an exchange service account specifying much less volume than can be expected
     * @then User gets rollback transaction
     */
    @Test
    fun rollbackTooLittleExchange() {
        Assertions.assertTimeoutPreemptively(timeoutDuration) {
            val tokenA = integrationHelper.createAsset().get()
            val tokenB = integrationHelper.createAsset().get()

            val userName = String.getRandomString(7)
            val userKeypair = Ed25519Sha3().generateKeypair()
            val userPubkey = userKeypair.public.toHexString()
            val res = registrationServiceEnvironment.registerV1(userName, userPubkey)
            assertEquals(200, res.statusCode)
            val userId = "$userName@$D3_DOMAIN"

            integrationHelper.addIrohaAssetTo(
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                "10000"
            )
            integrationHelper.addIrohaAssetTo(
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenB,
                "0.00000001"
            )

            integrationHelper.addIrohaAssetTo(
                userId,
                tokenA,
                "1"
            )
            integrationHelper.transferAssetIrohaFromClient(
                userId,
                userKeypair,
                userId,
                exchangerServiceEnvironment.cryptoExchangerAccountId,
                tokenA,
                tokenB,
                "0.000000000000000001"
            )

            Thread.sleep(TRANSFER_WAIT_TIME)

            val xorBalance = integrationHelper.getIrohaAccountBalance(userId, tokenA)
            assertEquals("1.000000000000000000", xorBalance)
            val etherBalance = integrationHelper.getIrohaAccountBalance(userId, tokenB)
            assertEquals("0", etherBalance)
        }
    }
}
