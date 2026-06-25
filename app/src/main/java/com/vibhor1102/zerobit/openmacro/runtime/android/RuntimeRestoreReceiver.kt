/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vibhor1102.zerobit.ZeroBitApplication

/**
 * Recreates approved desired subscriptions after Android clears alarms on
 * reboot or replaces the installed package.
 */
class RuntimeRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (
            intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        (context?.applicationContext as? ZeroBitApplication)
            ?.runtimeController
            ?.start()
    }
}
