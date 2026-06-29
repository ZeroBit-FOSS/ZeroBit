/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.describeValueSource
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireTextSource
import com.vibhor1102.zerobit.openmacro.capability.validateTextSource
import com.vibhor1102.zerobit.openmacro.capability.valueSource
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.MAX_MAP_QUERY_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.isValidMapQuery
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object OpenMapLocationAction : CapabilityDefinition {
    override val type = "android.map.open"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Open map location"
    override val description = "Opens a bounded location search in a map app."
    override val creation = CapabilityCreation(
        idBase = "open-map-location",
        setup = CapabilitySetup(fieldKeys = listOf("query")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "query",
            label = "Location",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Address, place name, or other map search text.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("query"), path))
        addAll(block.requireTextSource("query", path, MAX_MAP_QUERY_LENGTH))
        val query = block.config["query"] as? MacroValue.Text
        if (query != null && query.value.length <= MAX_MAP_QUERY_LENGTH && !isValidMapQuery(query.value)) {
            add(
                ValidationIssue(
                    path = "$path.config.query",
                    code = "invalid_map_query",
                    message = "Use location search text without control characters.",
                ),
            )
        }
    }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = block.validateTextSource("query", path, document, registry)

    override fun explain(block: MacroBlock): String =
        "Open a map search for ${block.describeValueSource("query")}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.OpenMapLocation(
        blockId = block.id,
        query = block.valueSource("query"),
    )
}
