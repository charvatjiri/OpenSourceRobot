package com.gabotapp

import org.json.JSONException
import org.json.JSONObject

class HighLevelCommandParser {

    fun parse(message: String): ParseResult {
        val payload = message.trim().removePrefix(HIGH_LEVEL_PREFIX).trim()
        if (payload.isBlank()) {
            return ParseResult.Error("empty high-level command")
        }

        if (payload.equals("stop", ignoreCase = true)) {
            return ParseResult.Success(HighLevelCommand.Stop)
        }
        if (payload.equals("status", ignoreCase = true)) {
            return ParseResult.Success(HighLevelCommand.Status)
        }
        if (payload.equals("cancel", ignoreCase = true)) {
            return ParseResult.Success(HighLevelCommand.Cancel)
        }
        if (payload.equals("pause", ignoreCase = true)) {
            return ParseResult.Success(HighLevelCommand.Pause)
        }
        if (payload.equals("resume", ignoreCase = true)) {
            return ParseResult.Success(HighLevelCommand.Resume)
        }

        return try {
            val json = JSONObject(payload)
            when (val action = json.optString("action").trim().lowercase()) {
                "collect" -> requiredString(json, "object") { objectName ->
                    HighLevelCommand.Collect(objectName)
                }
                "goto" -> requiredString(json, "target") { target ->
                    val objectName = json.optString("object").trim().ifBlank { null }
                    HighLevelCommand.GoTo(target, objectName)
                }
                "look" -> requiredString(json, "direction") { direction ->
                    when (direction.lowercase()) {
                        "left" -> HighLevelCommand.Look(HighLevelCommand.Direction.LEFT)
                        "right" -> HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT)
                        "center" -> HighLevelCommand.Look(HighLevelCommand.Direction.CENTER)
                        else -> return ParseResult.Error("invalid look direction: $direction")
                    }
                }
                "stop" -> ParseResult.Success(HighLevelCommand.Stop)
                "status" -> ParseResult.Success(HighLevelCommand.Status)
                "cancel" -> ParseResult.Success(HighLevelCommand.Cancel)
                "pause" -> ParseResult.Success(HighLevelCommand.Pause)
                "resume" -> ParseResult.Success(HighLevelCommand.Resume)
                "" -> ParseResult.Error("missing high-level action")
                else -> ParseResult.Error("unknown high-level action: $action")
            }
        } catch (e: JSONException) {
            ParseResult.Error("invalid high-level JSON: ${e.message}")
        }
    }

    private inline fun requiredString(
        json: JSONObject,
        field: String,
        createCommand: (String) -> HighLevelCommand
    ): ParseResult {
        val value = json.optString(field).trim()
        return if (value.isBlank()) {
            ParseResult.Error("missing high-level field: $field")
        } else {
            ParseResult.Success(createCommand(value))
        }
    }

    sealed class ParseResult {
        data class Success(val command: HighLevelCommand) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    companion object {
        const val HIGH_LEVEL_PREFIX = "hl:"
    }
}
