/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import android.content.Context
import android.content.Intent

class AndroidLauncherAppCatalog(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun listApps(): List<LauncherAppOption> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { resolved ->
                    val packageName = resolved.activityInfo?.packageName
                        ?: return@mapNotNull null
                    LauncherAppOption(
                        label = resolved.loadLabel(packageManager)?.toString()
                            ?.takeIf(String::isNotBlank)
                            ?: packageName,
                        packageName = packageName,
                    )
                }
                .let(::normalizeLauncherApps)
        } catch (_: RuntimeException) {
            emptyList()
        }
    }
}
