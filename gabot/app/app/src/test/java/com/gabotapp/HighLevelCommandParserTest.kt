package com.gabotapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighLevelCommandParserTest {
    private val parser = HighLevelCommandParser()

    @Test
    fun parsesSupportedCommands() {
        assertEquals(
            HighLevelCommand.Collect("apple"),
            success("hl:{\"action\":\"collect\",\"object\":\"apple\"}")
        )
        assertEquals(
            HighLevelCommand.GoTo("visible_object", "apple"),
            success("hl:{\"action\":\"goto\",\"target\":\"visible_object\",\"object\":\"apple\"}")
        )
        assertEquals(
            HighLevelCommand.Look(HighLevelCommand.Direction.LEFT),
            success("hl:{\"action\":\"look\",\"direction\":\"left\"}")
        )
        assertEquals(HighLevelCommand.Stop, success("hl:{\"action\":\"stop\"}"))
        assertEquals(HighLevelCommand.Stop, success("hl:stop"))
        assertEquals(
            HighLevelCommand.GoTo("visible_object", null),
            success("hl:{\"action\":\"goto\",\"target\":\"visible_object\"}")
        )
    }

    @Test
    fun rejectsMissingAndInvalidParameters() {
        assertTrue(parser.parse("hl:{\"action\":\"collect\"}") is HighLevelCommandParser.ParseResult.Error)
        assertTrue(parser.parse("hl:{\"action\":\"look\",\"direction\":\"up\"}") is HighLevelCommandParser.ParseResult.Error)
        assertTrue(parser.parse("hl:not-json") is HighLevelCommandParser.ParseResult.Error)
        assertTrue(parser.parse("hl:{\"action\":\"dance\"}") is HighLevelCommandParser.ParseResult.Error)
    }

    private fun success(message: String): HighLevelCommand {
        val result = parser.parse(message)
        assertTrue(result is HighLevelCommandParser.ParseResult.Success)
        return (result as HighLevelCommandParser.ParseResult.Success).command
    }
}
