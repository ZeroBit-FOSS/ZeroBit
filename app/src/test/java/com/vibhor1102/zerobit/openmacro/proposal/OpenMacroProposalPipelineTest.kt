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

    @Test
    fun explainsBoundedIntentActionsInPlainEnglish() {
        val proposal = ready(intentActionsSource())

        assertEquals(
            listOf(
                "Open Android app details for package com.example.chat.",
                "Open Android notification settings for package com.example.chat.",
                "Share \u201cHello there\u201d with package com.example.chat.",
            ),
            proposal.explanation.blocksIn(CapabilityLane.ACTION).map { it.summary },
        )
        assertTrue(proposal.explanation.requiredPermissions.isEmpty())
    }

    @Test
    fun changingIntentTargetRequiresApprovalWithBeforeAndAfterExplanation() {
        val approved = ApprovedMacroSnapshot.from(ready(intentActionsSource()))
        val changed = ready(
            intentActionsSource().replace(
                "package: com.example.chat",
                "package: com.example.mail",
            ),
            approved,
        )

        assertTrue(changed.comparison.behaviorChanged)
        assertTrue(changed.comparison.approvalRequired)
        assertEquals(
            listOf(
                BehaviorChangeKind.BLOCK_CHANGED,
                BehaviorChangeKind.BLOCK_CHANGED,
                BehaviorChangeKind.BLOCK_CHANGED,
            ),
            changed.comparison.changes.map { it.kind },
        )
        assertEquals(
            listOf(
                "Open Android app details for package com.example.chat.",
                "Open Android notification settings for package com.example.chat.",
                "Share \u201cHello there\u201d with package com.example.chat.",
            ),
            changed.comparison.changes.map { it.before },
        )
        assertEquals(
            listOf(
                "Open Android app details for package com.example.mail.",
                "Open Android notification settings for package com.example.mail.",
                "Share \u201cHello there\u201d with package com.example.mail.",
            ),
            changed.comparison.changes.map { it.after },
        )
    }

    @Test
    fun groupedActionsContributePermissionsToExplanation() {
        val proposal = ready(groupedSmsSource())

        assertEquals(
            setOf(AndroidPermission.SEND_SMS),
            proposal.explanation.requiredPermissions,
        )
        assertEquals(
            setOf(AndroidPermission.SEND_SMS),
            proposal.runtimePlan.requiredPermissions,
        )
        val summary = proposal.explanation.blocksIn(CapabilityLane.ACTION).single().summary
        assertTrue(summary.contains("Run 1 grouped action"))
        assertTrue(summary.contains("Send"))
        assertTrue(summary.contains("Hello"))
    }

    @Test
    fun groupedActionChildChangeExplainsNestedBeforeAndAfter() {
        val approved = ApprovedMacroSnapshot.from(ready(groupedSmsSource()))
        val changed = ready(
            groupedSmsSource().replace("message: Hello", "message: Updated"),
            approved,
        )

        assertEquals(
            listOf(BehaviorChangeKind.BLOCK_CHANGED),
            changed.comparison.changes.map { it.kind },
        )
        val change = changed.comparison.changes.single()
        assertEquals("group", change.blockId)
        assertTrue(change.before.orEmpty().contains("Hello"))
        assertTrue(change.after.orEmpty().contains("Updated"))
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

    private fun intentActionsSource() = """
        format: openmacro/v0.1
        metadata:
          id: intent-actions
          name: Intent actions
        triggers:
          - id: charger-connected
            type: android.power.connected
        actions:
          - id: open-details
            type: android.app.details
            config:
              package: com.example.chat
          - id: open-notification-settings
            type: android.app.notification-settings
            config:
              package: com.example.chat
          - id: share-text
            type: android.intent.share-text
            config:
              package: com.example.chat
              text: Hello there
    """.trimIndent() + "\n"

    private fun groupedSmsSource() = """
        format: openmacro/v0.1
        metadata:
          id: grouped-sms
          name: Grouped SMS
        triggers:
          - id: charger-connected
            type: android.power.connected
        actions:
          - id: group
            type: openmacro.action.group
            config:
              failurePolicy: stop
              actions:
                - id: sms
                  type: android.sms.send
                  config:
                    phoneNumber: "+1234567890"
                    message: Hello
    """.trimIndent() + "\n"
}
