/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import java.math.BigDecimal
import java.security.MessageDigest
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.events.AliasEvent
import org.snakeyaml.engine.v2.events.CollectionEndEvent
import org.snakeyaml.engine.v2.events.CollectionStartEvent
import org.snakeyaml.engine.v2.events.DocumentStartEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.ScalarEvent
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.schema.JsonSchema

object OpenMacroYamlReader {
    const val MAX_SOURCE_CODE_POINTS = 256 * 1024
    const val MAX_NESTING_DEPTH = 64

    private val legacyYamlBooleans = setOf(
        "y",
        "yes",
        "n",
        "no",
        "on",
        "off",
    )

    private val settings: LoadSettings = LoadSettings.builder()
        .setLabel("OpenMacro source")
        .setSchema(JsonSchema())
        .setAllowDuplicateKeys(false)
        .setAllowRecursiveKeys(false)
        .setAllowNonScalarKeys(false)
        .setMaxAliasesForCollections(0)
        .setCodePointLimit(MAX_SOURCE_CODE_POINTS)
        .setUseMarks(true)
        .build()

    fun read(sourceText: String): OpenMacroSourceResult {
        if (sourceText.codePointCount(0, sourceText.length) > MAX_SOURCE_CODE_POINTS) {
            return failure(
                code = "source_too_large",
                message = "OpenMacro files may contain at most $MAX_SOURCE_CODE_POINTS Unicode characters.",
            )
        }

        return try {
            val eventIssue = inspectEvents(sourceText)
            if (eventIssue != null) {
                OpenMacroSourceResult.Failure(listOf(eventIssue))
            } else {
                val root = Compose(settings).composeString(sourceText).orElse(null)
                    ?: return failure("empty_source", "The OpenMacro file is empty.")
                val document = decodeDocument(root)
                OpenMacroSourceResult.Success(
                    OpenMacroSource(
                        document = document,
                        originalText = sourceText,
                        fingerprint = sha256(sourceText),
                    ),
                )
            }
        } catch (problem: SourceProblem) {
            OpenMacroSourceResult.Failure(listOf(problem.issue))
        } catch (problem: MarkedYamlEngineException) {
            val mark = problem.problemMark.orElse(null)
            OpenMacroSourceResult.Failure(
                listOf(
                    SourceIssue(
                        code = "invalid_yaml",
                        message = problem.problem ?: "The YAML source is invalid.",
                        line = mark?.line?.plus(1),
                        column = mark?.column?.plus(1),
                    ),
                ),
            )
        } catch (problem: YamlEngineException) {
            failure(
                code = "invalid_yaml",
                message = problem.message ?: "The YAML source is invalid.",
            )
        }
    }

    private fun inspectEvents(sourceText: String): SourceIssue? {
        var documentCount = 0
        var depth = 0

        for (event in Parse(settings).parseString(sourceText)) {
            when (event) {
                is DocumentStartEvent -> {
                    documentCount += 1
                    if (documentCount > 1) {
                        return event.issue(
                            code = "multiple_documents",
                            message = "One OpenMacro file may contain only one YAML document.",
                        )
                    }
                    if (event.specVersion.isPresent || event.tags.isNotEmpty()) {
                        return event.issue(
                            code = "yaml_directive_not_allowed",
                            message = "OpenMacro does not allow YAML or tag directives.",
                        )
                    }
                }

                is AliasEvent -> return event.issue(
                    code = "alias_not_allowed",
                    message = "OpenMacro does not allow YAML aliases.",
                )

                is CollectionStartEvent -> {
                    if (event.anchor.isPresent) {
                        return event.issue(
                            code = "anchor_not_allowed",
                            message = "OpenMacro does not allow YAML anchors.",
                        )
                    }
                    if (event.tag.isPresent) {
                        return event.issue(
                            code = "tag_not_allowed",
                            message = "OpenMacro does not allow explicit YAML tags.",
                        )
                    }
                    depth += 1
                    if (depth > MAX_NESTING_DEPTH) {
                        return event.issue(
                            code = "nesting_too_deep",
                            message = "OpenMacro YAML may be nested at most $MAX_NESTING_DEPTH levels.",
                        )
                    }
                }

                is CollectionEndEvent -> depth -= 1

                is ScalarEvent -> {
                    if (event.anchor.isPresent) {
                        return event.issue(
                            code = "anchor_not_allowed",
                            message = "OpenMacro does not allow YAML anchors.",
                        )
                    }
                    if (event.tag.isPresent) {
                        return event.issue(
                            code = "tag_not_allowed",
                            message = "OpenMacro does not allow explicit YAML tags.",
                        )
                    }
                    if (
                        event.scalarStyle == ScalarStyle.PLAIN &&
                        event.value.lowercase() in legacyYamlBooleans
                    ) {
                        return event.issue(
                            code = "ambiguous_scalar",
                            message = "'${event.value}' is ambiguous YAML. Quote it if you mean text.",
                        )
                    }
                }
            }
        }
        return null
    }

    private fun decodeDocument(root: Node): OpenMacroDocument {
        val map = root.mapping("$")
        map.requireOnlyKeys(
            allowed = setOf("format", "metadata", "triggers", "conditions", "actions"),
            path = "$",
        )

        return OpenMacroDocument(
            format = map.required("format", "$").text("$.format"),
            metadata = decodeMetadata(map.required("metadata", "$")),
            triggers = decodeBlocks(map.required("triggers", "$"), "$.triggers"),
            conditions = map.optional("conditions")?.let {
                decodeBlocks(it, "$.conditions")
            }.orEmpty(),
            actions = decodeBlocks(map.required("actions", "$"), "$.actions"),
        )
    }

    private fun decodeMetadata(node: Node): MacroMetadata {
        val path = "$.metadata"
        val map = node.mapping(path)
        map.requireOnlyKeys(setOf("id", "name", "description"), path)
        return MacroMetadata(
            id = map.required("id", path).text("$path.id"),
            name = map.required("name", path).text("$path.name"),
            description = map.optional("description")?.text("$path.description"),
        )
    }

    private fun decodeBlocks(node: Node, path: String): List<MacroBlock> =
        node.sequence(path).mapIndexed { index, blockNode ->
            val blockPath = "$path[$index]"
            val map = blockNode.mapping(blockPath)
            map.requireOnlyKeys(setOf("id", "type", "config"), blockPath)
            MacroBlock(
                id = map.required("id", blockPath).text("$blockPath.id"),
                type = map.required("type", blockPath).text("$blockPath.type"),
                config = map.optional("config")?.let {
                    decodeConfigMap(it, "$blockPath.config")
                }.orEmpty(),
            )
        }

    private fun decodeConfigMap(node: Node, path: String): Map<String, MacroValue> =
        node.mapping(path).entries.associate { (key, value) ->
            key to decodeMacroValue(value, "$path.$key")
        }

    private fun decodeMacroValue(node: Node, path: String): MacroValue = when (node) {
        is ScalarNode -> when (node.tag) {
            Tag.STR -> MacroValue.Text(node.value)
            Tag.INT, Tag.FLOAT -> {
                val number = node.value.toBigDecimalOrNull()
                    ?: node.problem(path, "invalid_number", "Value must be a finite JSON number.")
                MacroValue.Number(number)
            }
            Tag.BOOL -> MacroValue.Boolean(node.value.toBooleanStrict())
            Tag.NULL -> MacroValue.Null
            else -> node.problem(
                path,
                "unsupported_value",
                "Only text, numbers, booleans, null, lists, and objects are allowed.",
            )
        }

        is SequenceNode -> MacroValue.ListValue(
            node.value.mapIndexed { index, child ->
                decodeMacroValue(child, "$path[$index]")
            },
        )

        is MappingNode -> MacroValue.ObjectValue(decodeConfigMap(node, path))

        else -> node.problem(
            path,
            "unsupported_value",
            "Only text, numbers, booleans, null, lists, and objects are allowed.",
        )
    }

    private fun Node.mapping(path: String): SourceMap {
        if (this !is MappingNode) {
            problem(path, "expected_object", "Expected an object at $path.")
        }

        val result = linkedMapOf<String, Node>()
        for (tuple in value) {
            val keyNode = tuple.keyNode
            if (keyNode !is ScalarNode || keyNode.tag != Tag.STR) {
                keyNode.problem(
                    path,
                    "invalid_key",
                    "Object keys must be plain text.",
                )
            }
            val key = keyNode.value
            if (key == "<<") {
                keyNode.problem(
                    path,
                    "merge_key_not_allowed",
                    "OpenMacro does not allow YAML merge keys.",
                )
            }
            if (result.put(key, tuple.valueNode) != null) {
                keyNode.problem(
                    path,
                    "duplicate_key",
                    "The key '$key' appears more than once.",
                )
            }
        }
        return SourceMap(result, this)
    }

    private fun Node.sequence(path: String): List<Node> {
        if (this !is SequenceNode) {
            problem(path, "expected_list", "Expected a list at $path.")
        }
        return value
    }

    private fun Node.text(path: String): String {
        if (this !is ScalarNode || tag != Tag.STR) {
            problem(path, "expected_text", "Expected text at $path.")
        }
        return value
    }

    private fun Node.problem(
        path: String,
        code: String,
        message: String,
    ): Nothing {
        val mark = startMark.orElse(null)
        throw SourceProblem(
            SourceIssue(
                code = code,
                message = message,
                path = path,
                line = mark?.line?.plus(1),
                column = mark?.column?.plus(1),
            ),
        )
    }

    private fun Event.issue(code: String, message: String): SourceIssue {
        val mark = startMark.orElse(null)
        return SourceIssue(
            code = code,
            message = message,
            line = mark?.line?.plus(1),
            column = mark?.column?.plus(1),
        )
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun failure(code: String, message: String) =
        OpenMacroSourceResult.Failure(listOf(SourceIssue(code = code, message = message)))

    private data class SourceMap(
        val entries: Map<String, Node>,
        val node: Node,
    ) {
        fun required(key: String, path: String): Node =
            entries[key] ?: fail(
                node = node,
                path = "$path.$key",
                code = "missing_key",
                message = "Required key '$key' is missing.",
            )

        fun optional(key: String): Node? = entries[key]

        fun requireOnlyKeys(allowed: Set<String>, path: String) {
            val unknown = entries.keys.firstOrNull { it !in allowed } ?: return
            fail(
                node = entries.getValue(unknown),
                path = "$path.$unknown",
                code = "unknown_key",
                message = "Key '$unknown' is not allowed at $path.",
            )
        }

        private fun fail(
            node: Node,
            path: String,
            code: String,
            message: String,
        ): Nothing {
            val mark = node.startMark.orElse(null)
            throw SourceProblem(
                SourceIssue(
                    code = code,
                    message = message,
                    path = path,
                    line = mark?.line?.plus(1),
                    column = mark?.column?.plus(1),
                ),
            )
        }
    }

    private class SourceProblem(
        val issue: SourceIssue,
    ) : RuntimeException(issue.message)
}
