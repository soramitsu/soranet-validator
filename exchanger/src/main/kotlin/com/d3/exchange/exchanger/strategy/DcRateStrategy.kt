/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.strategy

import com.d3.commons.util.GsonInstance
import com.d3.exchange.exchanger.dto.RatesResponse
import java.math.BigDecimal

/**
 * Rate strategy based on http querying of data collector service
 */
class DcRateStrategy(
    private val baseRateUrl: String,
    feeFraction: BigDecimal
) : RateStrategy(feeFraction) {

    private val gson = GsonInstance.get()

    override fun getAmount(from: String, to: String, amount: BigDecimal): BigDecimal {
        val rate = getRateFor(from, to)
        val amountWithRespectToFee = getAmountWithRespectToFee(amount)
        return rate.multiply(amountWithRespectToFee)
    }

    /**
     * Queries dc and parses its response
     */
    private fun getRateFor(assetId: String, baseAssetId: String): BigDecimal {
        val response = khttp.get(
            baseRateUrl,
            params = mapOf(
                ASSETS_PARAM_NAME to assetId,
                BASE_ASSET_PARAM_NAME to baseAssetId
            )
        )
        if (response.statusCode != 200) {
            throw IllegalStateException("Couldn't query data collector, response: ${response.text}")
        }
        val ratesResponse = gson.fromJson(response.text, RatesResponse::class.java)
        val rate = BigDecimal(ratesResponse?.rates?.get(assetId) ?: "0")
        if (rate.signum() == 0) {
            throw IllegalStateException("Asset not found in data collector")
        }
        return rate
    }

    companion object {
        const val ASSETS_PARAM_NAME = "assets"
        const val BASE_ASSET_PARAM_NAME = "base"
    }
}
