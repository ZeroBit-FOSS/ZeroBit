/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.proposal

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane

data class ProposalComparison(
    val sourceChanged: Boolean,
    val behaviorChanged: Boolean,
    val approvalRequired: Boolean,
    val changes: List<BehaviorChange>,
    val permissionsAdded: Set<AndroidPermission>,
    val permissionsRemoved: Set<AndroidPermission>,
)

data class BehaviorChange(
    val kind: BehaviorChangeKind,
    val lane: CapabilityLane? = null,
    val blockId: String? = null,
    val before: String? = null,
    val after: String? = null,
)

enum class BehaviorChangeKind {
    NEW_MACRO,
    MACRO_ID_CHANGED,
    BLOCK_ADDED,
    BLOCK_REMOVED,
    BLOCK_CHANGED,
    BLOCK_REORDERED,
    CONDITION_TREE_CHANGED,
}
