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

        return try {
            val json = JSONObject(payload)
            when (val action = json.optString("action").trim().lowercase()) {
                "stop" -> ParseResult.Success(HighLevelCommand.Stop)
                "" -> ParseResult.Error("missing high-level action")
                else -> ParseResult.Error("unknown high-level action: $action")
            }
        } catch (e: JSONException) {
            ParseResult.Error("invalid high-level JSON: ${e.message}")
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
