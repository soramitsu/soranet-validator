/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.util

import com.d3.chainadapter.client.ReliableIrohaChainListener
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.consumer.IrohaConsumerImpl
import com.d3.commons.util.createPrettySingleThreadPool
import com.d3.commons.util.getRandomString
import com.d3.exchange.exchanger.config.EXCHANGER_SERVICE_NAME
import com.d3.exchange.exchanger.config.rmqConfig
import com.d3.exchange.exchanger.context.CurveExchangerContext
import com.d3.exchange.exchanger.context.DcExchangerContext
import com.d3.exchange.exchanger.service.ExchangerService
import com.d3.exchange.exchanger.strategy.CurveRateStrategy
import com.d3.exchange.exchanger.strategy.DcRateStrategy
import integration.helper.DockerComposeStarter.Companion.dcContainerIpProperty
import integration.helper.IrohaIntegrationHelperUtil
import java.io.Closeable
import java.math.BigDecimal

/**
 * Environment for exchanger service running in tests
 */
class ExchangerServiceTestEnvironment(private val integrationHelper: IrohaIntegrationHelperUtil) :
    Closeable {

    val cryptoExchangerAccountId = integrationHelper.accountHelper.cryptoExchangerAccount.accountId

    val fiatExchangerAccountId = integrationHelper.accountHelper.fiatExchangerAccount.accountId

    private val testAccountId = integrationHelper.testCredential.accountId

    private val cryptoExchangerCredential = IrohaCredential(
        cryptoExchangerAccountId,
        integrationHelper.accountHelper.cryptoExchangerAccount.keyPair
    )

    private val fiatExchangerCredential = IrohaCredential(
        fiatExchangerAccountId,
        integrationHelper.accountHelper.fiatExchangerAccount.keyPair
    )

    private val cryptoIrohaConsumer = IrohaConsumerImpl(cryptoExchangerCredential, integrationHelper.irohaAPI)

    private val fiatIrohaConsumer = IrohaConsumerImpl(fiatExchangerCredential, integrationHelper.irohaAPI)

    private val infoIrohaConsumer =
        IrohaConsumerImpl(integrationHelper.testCredential, integrationHelper.irohaAPI)

    private val chainListener = ReliableIrohaChainListener(
        rmqConfig,
        "exchanger_blocks_${String.getRandomString(5)}",
        createPrettySingleThreadPool(EXCHANGER_SERVICE_NAME, "rmq-consumer")
    )

    private val queryHelper = integrationHelper.queryHelper

    private lateinit var exchangerService: ExchangerService

    fun init() {
        val feeFraction = BigDecimal(0.99)
        var property = System.getProperty(dcContainerIpProperty)
        if (property.isNullOrBlank()) {
            property = "http://data-collector:8080"
        }
        val baseRateUrl = "http://$property/v1/rates"
        exchangerService = ExchangerService(
            chainListener,
            listOf(
                CurveExchangerContext(
                    cryptoIrohaConsumer,
                    infoIrohaConsumer,
                    queryHelper,
                    CurveRateStrategy(
                        cryptoExchangerAccountId,
                        queryHelper,
                        feeFraction
                    ),
                    listOf(testAccountId)
                ),
                DcExchangerContext(
                    fiatIrohaConsumer,
                    infoIrohaConsumer,
                    queryHelper,
                    DcRateStrategy(
                        baseRateUrl,
                        feeFraction
                    ),
                    listOf(testAccountId)
                )
            )
        )
        exchangerService.start()
    }

    override fun close() {
        exchangerService.close()
        integrationHelper.close()
    }
}
