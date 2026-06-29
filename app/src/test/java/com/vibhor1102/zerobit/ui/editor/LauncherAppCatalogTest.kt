/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAppCatalogTest {
    @Test
    fun normalizesLauncherAppsDeterministicallyAndKeepsOnePackage() {
        val normalized = normalizeLauncherApps(
            listOf(
                LauncherAppOption("Zulu", "com.example.zulu"),
                LauncherAppOption("Alpha duplicate", "com.example.alpha"),
                LauncherAppOption("alpha", "com.example.alpha"),
                LauncherAppOption("Blank", ""),
            ),
        )

        assertEquals(
            listOf("com.example.alpha", "com.example.zulu"),
            normalized.map { it.packageName },
        )
    }

    @Test
    fun filtersByLabelOrExactPackageTextAndHonorsLimit() {
        val apps = normalizeLauncherApps(
            listOf(
                LauncherAppOption("Camera", "com.example.camera"),
                LauncherAppOption("Notes", "org.example.notes"),
            ),
        )

        assertEquals(apps, filterLauncherApps(apps, "  "))
        assertEquals(
            listOf("com.example.camera"),
            filterLauncherApps(apps, "CAMERA").map { it.packageName },
        )
        assertEquals(
            listOf("org.example.notes"),
            filterLauncherApps(apps, "org.example").map { it.packageName },
        )
        assertTrue(filterLauncherApps(apps, "missing").isEmpty())
        assertEquals(1, normalizeLauncherApps(apps, limit = 1).size)
    }
}
