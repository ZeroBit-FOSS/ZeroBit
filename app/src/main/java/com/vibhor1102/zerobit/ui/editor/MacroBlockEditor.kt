/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument

object MacroBlockEditor {
    fun updateConfig(
        document: OpenMacroDocument,
        blockId: String,
        key: String,
        value: MacroValue?,
    ): BlockEditResult {
        var matches = 0
        fun update(block: MacroBlock): MacroBlock {
            if (block.id != blockId) {
                return block
            }
            matches += 1
            val updatedConfig = block.config.toMutableMap().apply {
                if (value == null) remove(key) else put(key, value)
            }
            return block.copy(config = updatedConfig)
        }

        val updated = document.copy(
            triggers = document.triggers.map(::update),
            conditions = document.conditions.map(::update),
            actions = document.actions.map(::update),
            conditionTree = document.conditionTree?.mapBlocks(::update),
        )
        return when (matches) {
            0 -> BlockEditResult.NotFound(blockId)
            1 -> BlockEditResult.Updated(updated)
            else -> BlockEditResult.Ambiguous(blockId, matches)
        }
    }
}

sealed interface BlockEditResult {
    data class Updated(val document: OpenMacroDocument) : BlockEditResult

    data class NotFound(val blockId: String) : BlockEditResult

    data class Ambiguous(
        val blockId: String,
        val matchCount: Int,
    ) : BlockEditResult
}

private fun MacroConditionNode.mapBlocks(
    transform: (MacroBlock) -> MacroBlock,
): MacroConditionNode = when (this) {
    is MacroConditionNode.Condition -> copy(block = transform(block))
    is MacroConditionNode.All -> copy(children = children.map { it.mapBlocks(transform) })
    is MacroConditionNode.Any -> copy(children = children.map { it.mapBlocks(transform) })
    is MacroConditionNode.Not -> copy(child = child.mapBlocks(transform))
}
