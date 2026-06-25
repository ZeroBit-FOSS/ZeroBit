/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.proposal

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMacroProposalPipelineTest {
    private val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())

    @Test
    fun createsApprovalReadyProposalWithPlainEnglishExplanation() {
        val result = pipeline.propose(validSource())

        require(result is ProposalResult.Ready)
        val proposal = result.proposal
        assertEquals("charger-greeting", proposal.explanation.macroId)
        assertEquals(
            listOf("Start when the phone is connected to external power."),
            proposal.explanation.blocksIn(CapabilityLane.TRIGGER).map { it.summary },
        )
        assertEquals(
            listOf("Continue only if the phone is unlocked."),
            proposal.explanation.blocksIn(CapabilityLane.CONDITION).map { it.summary },
        )
        assertEquals(
            setOf(AndroidPermission.POST_NOTIFICATIONS),
            proposal.explanation.requiredPermissions,
        )
        assertTrue(proposal.comparison.approvalRequired)
        assertEquals(
            listOf(BehaviorChangeKind.NEW_MACRO),
            proposal.comparison.changes.map { it.kind },
        )
    }

    @Test
    fun keepsSourceAndValidationFailuresSeparate() {
        val malformed = pipeline.propose("format: [")
        require(malformed is ProposalResult.SourceRejected)
        assertEquals("invalid_yaml", malformed.issues.single().code)

        val unsupported = pipeline.propose(
            validSource().replace(
                "android.notification.show",
                "android.future.teleport",
            ),
        )
        require(unsupported is ProposalResult.ValidationRejected)
        assertEquals("unsupported_capability", unsupported.issues.single().code)
        assertTrue(unsupported.source.originalText.contains("android.future.teleport"))
    }

    @Test
    fun harmlessSourceAndMetadataEditsDoNotRequireBehaviorApproval() {
        val original = ready(validSource())
        val approved = ApprovedMacroSnapshot.from(original)
        val editedText = validSource()
            .replace("name: Charger greeting", "name: Friendly charger greeting")
            .replace(
                "format: openmacro/v0.1",
                "# Edited by a human\nformat: openmacro/v0.1",
            )

        val edited = ready(editedText, approved)

        assertTrue(edited.comparison.sourceChanged)
        assertFalse(edited.comparison.behaviorChanged)
        assertFalse(edited.comparison.approvalRequired)
        assertTrue(edited.comparison.changes.isEmpty())
        assertTrue(edited.comparison.permissionsAdded.isEmpty())
    }

    @Test
    fun actionConfigurationChangeRequiresApprovalAndExplainsTheDifference() {
        val original = ready(validSource())
        val approved = ApprovedMacroSnapshot.from(original)
        val changed = ready(
            validSource().replace(
                "message: The charger is connected.",
                "message: Time to charge.",
            ),
            approved,
        )

        assertTrue(changed.comparison.behaviorChanged)
        assertTrue(changed.comparison.approvalRequired)
        assertEquals(
            listOf(BehaviorChangeKind.BLOCK_CHANGED),
            changed.comparison.changes.map { it.kind },
        )
        val change = changed.comparison.changes.single()
        assertEquals("show-message", change.blockId)
        assertTrue(change.before.orEmpty().contains("The charger is connected."))
        assertTrue(change.after.orEmpty().contains("Time to charge."))
    }

    @Test
    fun actionOrderChangeIsVisibleAndRequiresApproval() {
        val sourcePrefix = validSource().substringBefore("actions:")
        val twoActions = sourcePrefix + """
            actions:
              - id: first-message
                type: android.notification.show
                config:
                  title: First
                  message: First message.
              - id: second-message
                type: android.notification.show
                config:
                  title: Second
                  message: Second message.
        """.trimIndent() + "\n"
        val reordered = sourcePrefix + """
            actions:
              - id: second-message
                type: android.notification.show
                config:
                  title: Second
                  message: Second message.
              - id: first-message
                type: android.notification.show
                config:
                  title: First
                  message: First message.
        """.trimIndent() + "\n"
        val approved = ApprovedMacroSnapshot.from(ready(twoActions))

        val proposal = ready(reordered, approved)

        assertTrue(proposal.comparison.approvalRequired)
        assertEquals(
            listOf(
                BehaviorChangeKind.BLOCK_REORDERED,
                BehaviorChangeKind.BLOCK_REORDERED,
            ),
            proposal.comparison.changes.map { it.kind },
        )
    }

    private fun ready(
        source: String,
        approved: ApprovedMacroSnapshot? = null,
    ): OpenMacroProposal {
        val result = pipeline.propose(source, approved)
        require(result is ProposalResult.Ready) {
            "Expected an approval-ready proposal, got $result"
        }
        return result.proposal
    }

    private fun validSource() = """
        format: openmacro/v0.1
        metadata:
          id: charger-greeting
          name: Charger greeting
        triggers:
          - id: charger-connected
            type: android.power.connected
        conditions:
          - id: device-is-unlocked
            type: android.device.unlocked
        actions:
          - id: show-message
            type: android.notification.show
            config:
              title: Charging started
              message: The charger is connected.
    """.trimIndent() + "\n"
}
