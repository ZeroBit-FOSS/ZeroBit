/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission

sealed interface AndroidPermissionRecovery {
    val explanation: String

    data class RequestRuntimePermission(
        val manifestPermission: String,
        override val explanation: String,
    ) : AndroidPermissionRecovery

    data class OpenSettings(
        val intent: Intent,
        override val explanation: String,
    ) : AndroidPermissionRecovery
}

object AndroidPermissionRecoveryFactory {
    fun create(
        context: Context,
        permission: AndroidPermission,
    ): AndroidPermissionRecovery = when (permission) {
        AndroidPermission.CAMERA ->
            AndroidPermissionRecovery.RequestRuntimePermission(
                Manifest.permission.CAMERA,
                "Allow ZeroBit to control the torch when an approved macro runs.",
            )
        AndroidPermission.POST_NOTIFICATIONS ->
            AndroidPermissionRecovery.RequestRuntimePermission(
                Manifest.permission.POST_NOTIFICATIONS,
                "Allow ZeroBit to show notifications created by approved macros.",
            )
        AndroidPermission.SEND_SMS ->
            AndroidPermissionRecovery.RequestRuntimePermission(
                Manifest.permission.SEND_SMS,
                "Allow ZeroBit to send SMS only when an approved macro runs.",
            )
        AndroidPermission.NOTIFICATION_LISTENER_ACCESS ->
            AndroidPermissionRecovery.OpenSettings(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                "Open Android notification access and enable ZeroBit.",
            )
        AndroidPermission.SCHEDULE_EXACT_ALARM_ACCESS ->
            AndroidPermissionRecovery.OpenSettings(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ),
                "Allow exact alarms for macros that must run at a precise time.",
            )
        AndroidPermission.ACCESS_NETWORK_STATE ->
            AndroidPermissionRecovery.OpenSettings(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
                "Open ZeroBit app settings to review network-state access.",
            )
    }
}
