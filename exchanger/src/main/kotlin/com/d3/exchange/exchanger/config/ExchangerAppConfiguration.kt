/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.config

import com.d3.chainadapter.client.RMQConfig
import com.d3.chainadapter.client.ReliableIrohaChainListener
import com.d3.commons.config.loadRawLocalConfigs
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.consumer.IrohaConsumerImpl
import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.d3.exchange.exchanger.context.CurveExchangerContext
import com.d3.exchange.exchanger.context.DcExchangerContext
import com.d3.exchange.exchanger.strategy.CurveRateStrategy
import com.d3.exchange.exchanger.strategy.DcRateStrategy
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Utils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

const val configFilename = "exchanger.properties"

val exchangerConfig =
    loadRawLocalConfigs(
        "exchanger",
        ExchangerConfig::class.java,
        configFilename
    )

val rmqConfig = loadRawLocalConfigs("rmq", RMQConfig::class.java, "rmq.properties")

val exchangerInfoConfig =
    loadRawLocalConfigs(
        "exchanger-info",
        // the same format for now
        ExchangerCurveContextConfig::class.java,
        configFilename
    )

val exchangerCurveConfig =
    loadRawLocalConfigs(
        "exchanger-crypto",
        ExchangerCurveContextConfig::class.java,
        configFilename
    )

val exchangerDcConfig =
    loadRawLocalConfigs(
        "exchanger-fiat",
        ExchangerDcConfig::class.java,
        configFilename
    )

const val EXCHANGER_SERVICE_NAME = "exchanger-service"

/**
 * Spring configuration for Notary Exchanger Service
 */
@Configuration
class ExchangerAppConfiguration {

    @Bean
    fun irohaAPI() = IrohaAPI(exchangerConfig.iroha.hostname, exchangerConfig.iroha.port)

    @Bean
    fun feeFraction() = BigDecimal(exchangerConfig.feeFraction)

    @Bean
    fun liquidityProviders() = exchangerConfig.liquidityProviders.split(",").toList()

    @Bean
    fun infoIrohaCredential() = IrohaCredential(exchangerInfoConfig.irohaCredential)

    @Bean
    fun curveIrohaCredential() = IrohaCredential(exchangerCurveConfig.irohaCredential)

    @Bean
    fun curveIrohaConsumer() =
        IrohaConsumerImpl(
            curveIrohaCredential(),
            irohaAPI()
        )

    @Bean
    fun curveAccountId() = exchangerCurveConfig.irohaCredential.accountId

    @Bean
    fun infoQueryAPI() =
        QueryAPI(
            irohaAPI(),
            exchangerInfoConfig.irohaCredential.accountId,
            Utils.parseHexKeypair(
                exchangerInfoConfig.irohaCredential.pubkey,
                exchangerInfoConfig.irohaCredential.privkey
            )
        )

    @Bean
    fun infoIrohaConsumer() = IrohaConsumerImpl(infoIrohaCredential(), irohaAPI())

    @Bean
    fun queryHelper() = IrohaQueryHelperImpl(infoQueryAPI())

    @Bean
    fun curveRateStrategy() =
        CurveRateStrategy(
            curveAccountId(),
            queryHelper(),
            feeFraction()
        )

    @Bean
    fun curveExchangerContext() =
        CurveExchangerContext(
            curveIrohaConsumer(),
            infoIrohaConsumer(),
            queryHelper(),
            curveRateStrategy(),
            liquidityProviders()
        )

    @Bean
    fun dcIrohaCredential() = IrohaCredential(exchangerDcConfig.irohaCredential)

    @Bean
    fun dcIrohaConsumer() =
        IrohaConsumerImpl(
            dcIrohaCredential(),
            irohaAPI()
        )

    @Bean
    fun dcRateStrategy() =
        DcRateStrategy(
            exchangerDcConfig.assetRateBaseUrl,
            feeFraction()
        )

    @Bean
    fun dcExchangerContext() =
        DcExchangerContext(
            dcIrohaConsumer(),
            infoIrohaConsumer(),
            queryHelper(),
            dcRateStrategy(),
            liquidityProviders()
        )

    @Bean
    fun contexts() = listOf(curveExchangerContext(), dcExchangerContext())

    @Bean
    fun chainListener() =
        ReliableIrohaChainListener(
            rmqConfig,
            exchangerConfig.irohaBlockQueue,
            autoAck = false
        )
}
