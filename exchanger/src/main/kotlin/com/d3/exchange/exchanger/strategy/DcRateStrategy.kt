/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.strategy

import com.d3.commons.util.GsonInstance
import com.d3.exchange.exchanger.dto.RatesResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * Rate strategy based on http querying of data collector service
 */
class DcRateStrategy(
    private val baseRateUrl: String,
    private val baseAssetId: String,
    feeFraction: BigDecimal
) : RateStrategy(feeFraction) {

    private val gson = GsonInstance.get()

    override fun getAmount(from: String, to: String, amount: BigDecimal): BigDecimal {
        val fromRate = getRateOrBaseAsset(from)
        val toRate = getRateOrBaseAsset(to)
        val amountWithRespectToFee = getAmountWithRespectToFee(amount)
        return toRate
            .multiply(amountWithRespectToFee)
            .divide(
                fromRate,
                MAX_PRECISION,
                RoundingMode.HALF_DOWN
            )
    }

    private fun getRateOrBaseAsset(assetId: String) =
        if (assetId == baseAssetId) {
            BigDecimal.ONE
        } else {
            getRateFor(assetId)
        }

    /**
     * Queries dc and parses its response
     */
    private fun getRateFor(assetId: String): BigDecimal {
        val response = khttp.get("$baseRateUrl?assets=${assetId.replace("#", "%23")}}")
        if (response.statusCode != 200) {
            throw IllegalStateException("Couldn't query data collector, response: ${response.text}")
        }
        val ratesResponse = gson.fromJson(response.text, RatesResponse::class.java)
        val rate = BigDecimal(Optional.ofNullable(ratesResponse?.rates?.get(assetId)).orElse("0"))
        if (rate.signum() == 0) {
            throw IllegalStateException("Asset not found in data collector")
        }
        return rate
    }

    companion object {
        const val MAX_PRECISION = 18
    }
}
