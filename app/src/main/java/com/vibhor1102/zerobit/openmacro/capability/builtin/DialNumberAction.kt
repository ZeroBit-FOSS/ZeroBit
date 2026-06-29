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
import com.vibhor1102.zerobit.openmacro.runtime.MAX_DIAL_NUMBER_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.isDialablePhoneNumber
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object DialNumberAction : CapabilityDefinition {
    override val type = "android.phone.dial"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Dial number"
    override val description = "Opens Android's dialer with a validated phone number."
    override val creation = CapabilityCreation(
        idBase = "dial-number",
        setup = CapabilitySetup(fieldKeys = listOf("phoneNumber")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "phoneNumber",
            label = "Phone Number",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Number to place in the dialer; ZeroBit does not place the call.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("phoneNumber"), path))
            addAll(
                block.requireTextSource(
                    "phoneNumber",
                    path,
                    maxLiteralLength = MAX_DIAL_NUMBER_LENGTH,
                ),
            )
            val literal = block.config["phoneNumber"] as? MacroValue.Text
            if (
                literal != null &&
                literal.value.length <= MAX_DIAL_NUMBER_LENGTH &&
                !isDialablePhoneNumber(literal.value)
            ) {
                add(
                    ValidationIssue(
                        path = "$path.config.phoneNumber",
                        code = "invalid_dial_number",
                        message = "Use a dialable number containing digits and standard dialer symbols.",
                    ),
                )
            }
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        block.validateTextSource("phoneNumber", path, document, registry)

    override fun explain(block: MacroBlock): String =
        "Open the dialer with ${block.describeValueSource("phoneNumber")}; do not place the call."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.DialNumber(
            blockId = block.id,
            phoneNumber = block.valueSource("phoneNumber"),
        )
}
