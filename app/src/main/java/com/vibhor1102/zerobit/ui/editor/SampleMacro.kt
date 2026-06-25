/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

object SampleMacro {
    val source = """
        format: openmacro/v0.1

        metadata:
          id: charger-greeting
          name: Charger greeting
          description: Show a message when the phone starts charging.

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
