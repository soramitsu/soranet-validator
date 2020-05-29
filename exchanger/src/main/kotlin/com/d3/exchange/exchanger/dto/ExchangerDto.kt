/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.dto


data class BdsResponseStatus(var code: String?, var message: String?) {
    // for databind
    private constructor() : this(null, null)

    companion object {
        private const val SUCCESS_MESSAGE = "Success"
        val SUCCESS = BdsResponseStatus(
            "OK",
            SUCCESS_MESSAGE
        )
    }
}

open class Conflictable(val status: BdsResponseStatus) {
    constructor() : this(BdsResponseStatus.SUCCESS)
    constructor(code: String, message: String) : this(
        BdsResponseStatus(code, message)
    )
}

class RatesResponse(
    val base: String? = null,
    val timestamp: String? = null,
    val rates: Map<String, String> = emptyMap()
) : Conflictable()