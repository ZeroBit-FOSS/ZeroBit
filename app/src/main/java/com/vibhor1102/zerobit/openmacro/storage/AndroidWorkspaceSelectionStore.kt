/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import android.content.Context
import android.net.Uri

class AndroidWorkspaceSelectionStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(
        "openmacro_workspace_selection",
        Context.MODE_PRIVATE,
    )

    fun selected(): AndroidWorkspaceSelection? =
        preferences.getString(KEY_TREE_URI, null)
            ?.let(Uri::parse)
            ?.let { uri ->
                AndroidWorkspaceSelection(
                    treeUri = uri,
                    label = uri.lastPathSegment ?: uri.toString(),
                )
            }

    fun save(treeUri: Uri): AndroidWorkspaceSelection {
        preferences.edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .apply()
        return AndroidWorkspaceSelection(
            treeUri = treeUri,
            label = treeUri.lastPathSegment ?: treeUri.toString(),
        )
    }

    private companion object {
        const val KEY_TREE_URI = "tree_uri"
    }
}

data class AndroidWorkspaceSelection(
    val treeUri: Uri,
    val label: String,
)
