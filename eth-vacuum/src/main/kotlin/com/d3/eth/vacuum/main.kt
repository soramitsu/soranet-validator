@file:JvmName("VacuumRelayMain")

package com.d3.eth.vacuum

import com.d3.commons.config.loadConfigs
import com.d3.commons.config.loadEthPasswords
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.util.ModelUtil
import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.github.kittinunf.result.*
import jp.co.soramitsu.iroha.java.IrohaAPI
import mu.KLogging

private const val RELAY_VACUUM_PREFIX = "relay-vacuum"
private val logger = KLogging().logger
/**
 * Entry point for moving all currency from relay contracts to master contract
 */
fun main(args: Array<String>) {
    loadConfigs(RELAY_VACUUM_PREFIX, RelayVacuumConfig::class.java, "/eth/vacuum.properties")
        .map { relayVacuumConfig ->
            executeVacuum(relayVacuumConfig, args)
        }
        .failure { ex ->
            logger.error("Cannot run vacuum", ex)
            System.exit(1)
        }
}

fun executeVacuum(
    relayVacuumConfig: RelayVacuumConfig,
    args: Array<String> = emptyArray()
): Result<Unit, Exception> {
    logger.info { "Run relay vacuum" }
    return ModelUtil.loadKeypair(
        relayVacuumConfig.vacuumCredential.pubkeyPath,
        relayVacuumConfig.vacuumCredential.privkeyPath
    )
        .map { keypair -> IrohaCredential(relayVacuumConfig.vacuumCredential.accountId, keypair) }
        .fanout {
            Result.of { IrohaAPI(relayVacuumConfig.iroha.hostname, relayVacuumConfig.iroha.port) }
        }.map { (credential, irohaAPI) ->
            IrohaQueryHelperImpl(irohaAPI, credential.accountId, credential.keyPair)
        }
        .fanout { loadEthPasswords(RELAY_VACUUM_PREFIX, "/eth/ethereum_password.properties", args) }
        .flatMap { (queryHelper, passwordConfig) ->
            RelayVacuum(relayVacuumConfig, passwordConfig, queryHelper).vacuum()
        }
}
