/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.context

import com.d3.commons.sidechain.iroha.consumer.IrohaConsumer
import com.d3.commons.sidechain.iroha.util.IrohaQueryHelper
import com.d3.commons.sidechain.iroha.util.ModelUtil
import com.d3.exchange.exchanger.exceptions.AssetNotFoundException
import com.d3.exchange.exchanger.exceptions.TooLittleAssetVolumeException
import com.d3.exchange.exchanger.strategy.RateStrategy
import com.d3.exchange.exchanger.util.addCommandIndex
import com.d3.exchange.exchanger.util.normalizeTransactionHash
import com.d3.exchange.exchanger.util.respectPrecision
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import iroha.protocol.BlockOuterClass
import iroha.protocol.Commands
import iroha.protocol.Primitive
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import java.math.BigDecimal
import java.util.*

/**
 * Context telling exchanger how to process inputs
 */
abstract class ExchangerContext(
    protected val exchangerIrohaConsumer: IrohaConsumer,
    protected val utilityIrohaConsumer: IrohaConsumer,
    protected val queryHelper: IrohaQueryHelper,
    private val rateStrategy: RateStrategy,
    protected val liquidityProviderAccounts: List<String>,
    protected val exchangerAccountId: String
) {

    init {
        val optional = queryHelper.getAccountDetails(
            exchangerAccountId,
            exchangerAccountId,
            GRANTED_KEY
        ).get()
        if (!optional.isPresent || optional.get() != GRANTED_VALUE) {
            val createdTime = System.currentTimeMillis()
            exchangerIrohaConsumer.send(
                Transaction.builder(exchangerAccountId, createdTime - (createdTime % MILLIS_IN_DAY))
                    .setQuorum(exchangerIrohaConsumer.getConsumerQuorum().get())
                    .setAccountDetail(exchangerAccountId, GRANTED_KEY, GRANTED_VALUE)
                    .grantPermission(
                        expansionTriggerAccountId,
                        Primitive.GrantablePermission.can_add_my_signatory
                    )
                    .grantPermission(
                        expansionTriggerAccountId,
                        Primitive.GrantablePermission.can_remove_my_signatory
                    )
                    .grantPermission(
                        expansionTriggerAccountId,
                        Primitive.GrantablePermission.can_set_my_quorum
                    )
                    .build()
            )
        }
    }

    /**
     * Performs conversion based on the block specified
     * @param block block to process
     */
    fun performConversions(block: BlockOuterClass.Block) {
        val payload = block.blockV1.payload
        payload.transactionsList.forEach { transaction ->
            val txHash = normalizeTransactionHash(Utils.toHexHash(transaction), payload.height)
            val reducedPayload = transaction.payload.reducedPayload
            val creationTime = reducedPayload.createdTime
            for (i in 0 until reducedPayload.commandsCount) {
                val command = reducedPayload.commandsList[i]
                if (command.hasTransferAsset()
                    && !liquidityProviderAccounts.contains(command.transferAsset.srcAccountId)
                    && command.transferAsset.destAccountId == exchangerAccountId
                ) {
                    val transferAsset = command.transferAsset
                    performConversion(transferAsset, addCommandIndex(txHash, i), creationTime)
                }
            }
        }
    }

    /**
     * Performs checking of data and converts assets using specified [RateStrategy]
     * If something goes wrong performs a rollback
     */
    private fun performConversion(
        exchangeCommand: Commands.TransferAsset,
        commandId: String,
        creationTime: Long
    ) {
        val sourceAsset = exchangeCommand.assetId
        val targetAsset = exchangeCommand.description
        val amount = exchangeCommand.amount
        val destAccountId = exchangeCommand.srcAccountId
        logger.info { "Got a conversion request from $destAccountId: $amount $sourceAsset to $targetAsset." }
        Result.of {
            val precision = queryHelper.getAssetPrecision(targetAsset).fold(
                { it },
                { throw AssetNotFoundException("Seems the asset $targetAsset does not exist.", it) }
            )

            val relevantAmount = rateStrategy.getAmount(sourceAsset, targetAsset, BigDecimal(amount))

            var respectPrecision = respectPrecision(relevantAmount.toPlainString(), precision)

            // If the result is not bigger than zero
            if (BigDecimal(respectPrecision) <= BigDecimal.ZERO) {
                throw TooLittleAssetVolumeException("Asset supplement is too low for specified conversion")
            }

            respectPrecision = negotiateAmount(commandId, respectPrecision)

            performTransferLogic(exchangeCommand, respectPrecision, creationTime)

        }.fold(
            { logger.info { "Successfully converted $amount of $sourceAsset to $targetAsset." } },
            {
                logger.error("Exchanger error occurred. Performing rollback.", it)

                ModelUtil.transferAssetIroha(
                    exchangerIrohaConsumer,
                    exchangerAccountId,
                    destAccountId,
                    sourceAsset,
                    "Conversion rollback transaction",
                    amount,
                    creationTime = creationTime
                ).failure { ex ->
                    logger.error("Error during rollback", ex)
                }
            })
    }

    /**
     * Customizable transfer logic
     */
    protected open fun performTransferLogic(
        originalCommand: Commands.TransferAsset,
        amount: String,
        creationTime: Long
    ) {
        val sourceAsset = originalCommand.assetId
        val targetAsset = originalCommand.description
        val destAccountId = originalCommand.srcAccountId

        ModelUtil.transferAssetIroha(
            exchangerIrohaConsumer,
            exchangerAccountId,
            destAccountId,
            targetAsset,
            "Conversion from $sourceAsset to $targetAsset",
            amount,
            creationTime = creationTime
        ).failure {
            throw it
        }
    }

    private fun negotiateAmount(commandId: String, amount: String): String {
        return ModelUtil.compareAndsetAccountDetail(
            utilityIrohaConsumer,
            utilityIrohaConsumer.creator,
            commandId,
            amount
        ).fold(
            {
                logger.info("Set amount $amount for operation $commandId")
                amount
            },
            {
                logger.warn("Other instance has set the amount for operation $commandId", it)
                var savedDetail: Optional<String>
                do {
                    logger.info("Querying Iroha for the amount of the operation $commandId", it)
                    savedDetail = queryHelper.getAccountDetails(
                        utilityIrohaConsumer.creator,
                        utilityIrohaConsumer.creator,
                        commandId
                    ).get()
                    if (!savedDetail.isPresent) {
                        logger.warn("Iroha detail has not been set yet, waiting $QUERY_TIMEOUT ms")
                        Thread.sleep(QUERY_TIMEOUT)
                    }
                } while (!savedDetail.isPresent)
                savedDetail.get()
            }
        )
    }

    companion object : KLogging() {
        const val expansionTriggerAccountId = "superuser@bootstrap"
        const val MILLIS_IN_DAY = 86400000
        const val QUERY_TIMEOUT = 1000L
        const val GRANTED_KEY = "granted"
        const val GRANTED_VALUE = "true"
    }
}
