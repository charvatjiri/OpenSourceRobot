package com.gabot.pcclient

import com.gabot.shared.BinaryControlState
import com.gabot.shared.ControllerCommands
import com.gabot.shared.WristPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerCommandsTest {
    @Test
    fun `controller commands match Gabot serial protocol`() {
        assertEquals("grab 0", ControllerCommands.GRAB_START)
        assertEquals("release 1", ControllerCommands.RELEASE_STOP)
        assertEquals("shoulder horizontal -40", ControllerCommands.ARM_LEFT)
        assertEquals("shoulder vertical 0", ControllerCommands.ARM_VERTICAL_STOP)
        assertEquals("wheels fb 15", ControllerCommands.WHEELS_FORWARD)
        assertEquals("wheels rl 0", ControllerCommands.WHEELS_RL_STOP)
    }

    @Test
    fun `wrist command contains current position`() {
        assertEquals("wrist horizontal 80", ControllerCommands.wristHorizontal(80))
        assertEquals("wrist vertical 100", ControllerCommands.wristVertical(100))
    }

    @Test
    fun `wrist movement is limited to safe range`() {
        assertEquals(150, WristPosition(horizontal = 150).moveHorizontal(1).horizontal)
        assertEquals(10, WristPosition(horizontal = 10).moveHorizontal(-1).horizontal)
        assertEquals(150, WristPosition(vertical = 150).moveVertical(1).vertical)
        assertEquals(50, WristPosition(vertical = 50).moveVertical(-1).vertical)
    }

    @Test
    fun `gripper controls alternate between on and off`() {
        listOf(
            ControllerCommands.GRAB_START to ControllerCommands.GRAB_STOP,
            ControllerCommands.RELEASE_START to ControllerCommands.RELEASE_STOP
        ).forEach { (onCommand, offCommand) ->
            val enabled = BinaryControlState().toggle(onCommand, offCommand)
            val disabled = enabled.state.toggle(onCommand, offCommand)

            assertEquals(true, enabled.state.isOn)
            assertEquals(onCommand, enabled.command)
            assertEquals(false, disabled.state.isOn)
            assertEquals(offCommand, disabled.command)
        }
    }
}
