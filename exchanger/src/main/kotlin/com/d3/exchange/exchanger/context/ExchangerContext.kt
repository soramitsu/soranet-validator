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
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import java.math.BigDecimal

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

    /**
     * Performs conversion based on the block specified
     * @param block block to process
     */
    fun performConversions(block: BlockOuterClass.Block) {
        var txHash = ""
        var creationTime = 0L
        block.blockV1.payload.transactionsList.map { transaction ->
            txHash = normalizeTransactionHash(Utils.toHexHash(transaction), block.blockV1.payload.height)
            creationTime = transaction.payload.reducedPayload.createdTime
            transaction.payload.reducedPayload.commandsList.filter { command ->
                command.hasTransferAsset()
                        && !liquidityProviderAccounts.contains(command.transferAsset.srcAccountId)
                        && command.transferAsset.destAccountId == exchangerAccountId
            }.map { command ->
                command.transferAsset
            }
        }.map { exchangeCommands ->
            exchangeCommands.forEach { command ->
                performConversion(command, addCommandIndex(txHash, command), creationTime)
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
                queryHelper.getAccountDetails(
                    utilityIrohaConsumer.creator,
                    utilityIrohaConsumer.creator,
                    commandId
                ).get().get()
            }
        )
    }

    companion object : KLogging()
}
