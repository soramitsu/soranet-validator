@file:JvmName("EthPreDeployMain")

package deploy

import com.github.kittinunf.result.failure
import com.github.kittinunf.result.fanout
import com.github.kittinunf.result.map
import config.EthereumConfig
import config.loadConfigs
import config.loadEthPasswords
import mu.KLogging
import sidechain.eth.util.DeployHelper
import java.io.File

private val logger = KLogging().logger

/**
 * Entry point to deploy smart contracts.
 * [args] should contain the list of notary ethereum addresses
 */
fun main(args: Array<String>) {
    logger.info { "Run predeploy with notary addresses: ${args.toList()}" }
    if (args.isEmpty()) {
        logger.error { "No notary ethereum addresses are provided." }
        System.exit(1)
    }

    loadConfigs("predeploy.ethereum", EthereumConfig::class.java, "/eth/predeploy.properties")
        .fanout { loadEthPasswords("predeploy", "/eth/ethereum_password.properties") }
        .map { (ethereumConfig, passwordConfig) -> DeployHelper(ethereumConfig, passwordConfig) }
        .map { deployHelper ->

            val relayRegistry = deployHelper.deployRelayRegistrySmartContract()
            val master = deployHelper.deployMasterSmartContract(relayRegistry.contractAddress, args.toList())

            File("master_eth_address").printWriter().use {
                it.print(master.contractAddress)
            }
            File("relay_registry_eth_address").printWriter().use {
                it.print(relayRegistry.contractAddress)
            }
            File("sora_token_eth_address").printWriter().use {
                it.print(master.tokens.send().get(0))
            }
        }
        .failure { ex ->
            logger.error("Cannot deploy smart contract", ex)
            System.exit(1)
        }
}
