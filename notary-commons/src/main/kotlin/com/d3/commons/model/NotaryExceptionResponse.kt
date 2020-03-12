/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.commons.model

enum class NotaryExceptionErrorCode {
    OK,
    WRONG_INPUT,
    ALREADY_REGISTERED
}

class NotaryException(
    val code: NotaryExceptionErrorCode,
    override val message: String?,
    override val cause: Throwable?
) : Exception(message, cause) {
    constructor(code: NotaryExceptionErrorCode, message: String?) : this(code, message, null)
}

class NotaryV1Exception(
    override val message: String?
) : Exception(message)

data class NotaryResponseStatus(var code: NotaryExceptionErrorCode?, var message: String?) {
    // for databind
    private constructor() : this(null, null)

    companion object {
        private const val SUCCESS_MESSAGE = "Success"
        val SUCCESS = NotaryResponseStatus(NotaryExceptionErrorCode.OK, SUCCESS_MESSAGE)
    }
}

open class NotaryGenericResponse(val status: NotaryResponseStatus) {
    constructor() : this(NotaryResponseStatus.SUCCESS)
    constructor(code: NotaryExceptionErrorCode, message: String) : this(NotaryResponseStatus(code, message))
}
