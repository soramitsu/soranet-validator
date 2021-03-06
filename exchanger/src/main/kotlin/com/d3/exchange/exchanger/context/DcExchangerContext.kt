/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.context

import com.d3.commons.sidechain.iroha.consumer.IrohaConsumer
import com.d3.commons.sidechain.iroha.util.IrohaQueryHelper
import com.d3.exchange.exchanger.strategy.DcRateStrategy
import com.github.kittinunf.result.failure
import iroha.protocol.Commands
import jp.co.soramitsu.iroha.java.Transaction

/**
 * [ExchangerContext] implementation that uses [DcRateStrategy]
 */
class DcExchangerContext(
    irohaConsumer: IrohaConsumer,
    utilityIrohaConsumer: IrohaConsumer,
    queryhelper: IrohaQueryHelper,
    dcRateStrategy: DcRateStrategy,
    liquidityProviderAccounts: List<String>
) : ExchangerContext(
    irohaConsumer,
    utilityIrohaConsumer,
    queryhelper,
    dcRateStrategy,
    liquidityProviderAccounts,
    irohaConsumer.creator
) {

    /**
     * Burns and mints corresponding fiat currencies together with sending
     */
    override fun performTransferLogic(
        originalCommand: Commands.TransferAsset,
        amount: String,
        creationTime: Long
    ) {
        val sourceAsset = originalCommand.assetId
        val srcAmount = originalCommand.amount
        val targetAsset = originalCommand.description
        val destAccountId = originalCommand.srcAccountId

        val transactionBuilder = Transaction.builder(exchangerAccountId, creationTime)
        transactionBuilder.subtractAssetQuantity(sourceAsset, srcAmount)
        transactionBuilder.addAssetQuantity(targetAsset, amount)
        transactionBuilder.setQuorum(exchangerIrohaConsumer.getConsumerQuorum().get())
        exchangerIrohaConsumer.send(
            transactionBuilder
                .transferAsset(
                    exchangerAccountId,
                    destAccountId,
                    targetAsset,
                    "Conversion from $sourceAsset to $targetAsset",
                    amount
                )
                .build()
        ).failure {
            throw it
        }
    }
}
