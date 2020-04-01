/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.notifications.service

import com.d3.notifications.event.AckEthWithdrawalProofEvent
import com.d3.notifications.event.EthWithdrawalProofsEvent
import com.github.kittinunf.result.Result

/**
 * Service that is used to handle specific Ethereum events
 */
interface EthSpecificNotificationService {

    /**
     * Notifies about having enough of withdrawal proofs in the Ethereum subsystem
     * @param ethWithdrawalProofsEvent - event to notify about
     * @return result of operation
     */
    fun notifyEthWithdrawalProofs(ethWithdrawalProofsEvent: EthWithdrawalProofsEvent): Result<Unit, Exception>

    /**
     * Notifies about successful withdrawal commit in the Ethereum subsystem
     * @param ackEthWithdrawalProofEvent - event to notify about
     * @return result of operation
     */
    fun notifyEthWithdrawalCommit(ackEthWithdrawalProofEvent: AckEthWithdrawalProofEvent): Result<Unit, Exception>
}
