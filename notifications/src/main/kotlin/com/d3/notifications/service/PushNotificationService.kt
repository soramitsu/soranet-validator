/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.notifications.service

import com.d3.notifications.event.FailedRegistrationNotifyEvent
import com.d3.notifications.event.RegistrationNotifyEvent
import com.d3.notifications.event.TransferNotifyEvent
import com.d3.notifications.push.WebPushAPIService
import com.github.kittinunf.result.Result

/**
 * Service that is used to notify D3 clients using push notifications
 */
class PushNotificationService(private val webPushAPIService: WebPushAPIService) :
    NotificationService {
    override fun notifyFailedRegistration(failedRegistrationNotifyEvent: FailedRegistrationNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            failedRegistrationNotifyEvent.accountId,
            "Failed registration in ${failedRegistrationNotifyEvent.subsystem}"
        )
    }

    override fun notifyRegistration(registrationNotifyEvent: RegistrationNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            registrationNotifyEvent.accountId,
            "Registration in ${registrationNotifyEvent.subsystem} with address ${registrationNotifyEvent.address}"
        )
    }

    override fun notifySendToClient(transferNotifyEvent: TransferNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            transferNotifyEvent.accountIdToNotify,
            "Transfer of ${transferNotifyEvent.amount} ${transferNotifyEvent.assetName}"
        )
    }

    override fun notifyReceiveFromClient(transferNotifyEvent: TransferNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            transferNotifyEvent.accountIdToNotify,
            "Transfer of ${transferNotifyEvent.amount} ${transferNotifyEvent.assetName}"
        )
    }

    override fun notifyRollback(transferNotifyEvent: TransferNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            transferNotifyEvent.accountIdToNotify,
            "Rollback of ${transferNotifyEvent.amount} ${transferNotifyEvent.assetName}"
        )
    }

    override fun notifyWithdrawal(transferNotifyEvent: TransferNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            transferNotifyEvent.accountIdToNotify,
            "Withdrawal of ${transferNotifyEvent.amount} ${transferNotifyEvent.assetName}"
        )
    }

    override fun notifyDeposit(transferNotifyEvent: TransferNotifyEvent): Result<Unit, Exception> {
        return webPushAPIService.push(
            transferNotifyEvent.accountIdToNotify,
            "Deposit of ${transferNotifyEvent.amount} ${transferNotifyEvent.assetName}"
        )
    }
}
