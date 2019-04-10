package com.d3.exchange.exchanger

import com.d3.commons.config.RMQConfig
import com.d3.commons.config.getConfigFolder
import com.d3.commons.config.loadRawConfigs
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.ReliableIrohaChainListener
import com.d3.commons.sidechain.iroha.consumer.IrohaConsumerImpl
import com.d3.commons.sidechain.iroha.util.ModelUtil
import com.d3.commons.util.createPrettySingleThreadPool
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

val exchangerConfig =
    loadRawConfigs("exchanger", ExchangerConfig::class.java, "${getConfigFolder()}/exchanger.properties")

val rmqConfig = loadRawConfigs("rmq", RMQConfig::class.java, "${getConfigFolder()}/rmq.properties")

const val EXCHANGER_SERVICE_NAME = "exchanger-service"

/**
 * Spring configuration for Notary Exchanger Service
 */
@Configuration
class ExchangerAppConfiguration {

    private final val keyPair = ModelUtil.loadKeypair(
        exchangerConfig.irohaCredential.pubkeyPath,
        exchangerConfig.irohaCredential.privkeyPath
    ).get()

    /** Exchanger service credentials */
    private val exchangerCredential = IrohaCredential(exchangerConfig.irohaCredential.accountId, keyPair)

    /** Iroha network connection */
    @Bean
    fun irohaAPI() = IrohaAPI(exchangerConfig.iroha.hostname, exchangerConfig.iroha.port)

    @Bean
    fun irohaConsumer() = IrohaConsumerImpl(
        exchangerCredential, irohaAPI()
    )

    /** Configurations for Exchanger Service */
    @Bean
    fun exchangerConfig() = exchangerConfig

    @Bean
    fun chainListener() = ReliableIrohaChainListener(
        rmqConfig,
        exchangerConfig.irohaBlockQueue,
        createPrettySingleThreadPool(EXCHANGER_SERVICE_NAME, "rmq-consumer")
    )

    @Bean
    fun queryAPI() = QueryAPI(irohaAPI(), exchangerConfig.irohaCredential.accountId, keyPair)

    @Bean
    fun liquidityProviders() = exchangerConfig.liquidityProviders.split(",").toList()
}