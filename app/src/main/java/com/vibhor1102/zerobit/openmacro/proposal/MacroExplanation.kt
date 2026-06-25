/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.proposal

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane

data class MacroExplanation(
    val macroId: String,
    val name: String,
    val blocks: List<BlockExplanation>,
    val requiredPermissions: Set<AndroidPermission>,
) {
    fun blocksIn(lane: CapabilityLane): List<BlockExplanation> =
        blocks.filter { it.lane == lane }
}

data class BlockExplanation(
    val blockId: String,
    val capabilityType: String,
    val lane: CapabilityLane,
    val displayName: String,
    val summary: String,
    val requiredPermissions: Set<AndroidPermission>,
)
