# GABOT Design Proposal - Variant 2

**GABOT / Variant 2**  
**GabotApp logic  |  Camera on Phone No. 1  |  Bluetooth server  |  USB serial control**

Project: OpenSourceRobot / GABOT  
Document type: Design proposal  
Date: April 28, 2026

---

## 1. Goals

This document proposes the Variant 2 architecture: the control logic will run inside GabotApp on Phone No. 1. This phone is mounted on the robot, acts as the Bluetooth server, communicates with the Arduino over the USB serial link, and uses its own camera for orientation.

Phone No. 2 sends only high-level commands. GabotApp receives them over Bluetooth, combines them with robot state and local camera perception, translates them into low-level GABOT protocol commands, and sends those commands to the Arduino over USB serial.

> **Recommended architecture:** Variant 2 with GabotApp as the Bluetooth server, camera-based orientation running on Phone No. 1, and high-level command interpretation inside GabotApp.

## 2. Target Architecture

| Layer | Component | Responsibility |
|---|---|---|
| Phone No. 2 | Client / operator | Sends high-level commands over Bluetooth |
| Phone No. 1 | GabotApp | BT server, camera, command interpretation, motion planning, USB serial bridge |
| Arduino Mega | GABOT firmware | Executes low-level motor commands and returns responses |

The main change from the simple bridge mode is that GabotApp will no longer forward every Bluetooth message directly to Serial. It will decide whether the input is a direct serial command or a high-level command that must be interpreted locally.

## 3. Data Flow

1. Phone No. 2 connects to the Bluetooth server in GabotApp.
2. Phone No. 2 sends a high-level command, for example `hl:{"action":"collect","object":"apple"}`.
3. GabotApp validates the command and passes it to `HighLevelCommandInterpreter`.
4. GabotApp obtains the current visual state from the camera through `VisionModule`.
5. `CommandPlanner` chooses a sequence of low-level commands.
6. `SerialCommandExecutor` sends the commands through `SerialInterface`.
7. The Arduino executes the commands and returns responses such as `OK ...` or `ERR ...`.
8. GabotApp sends status, progress, and errors back to the Bluetooth client.

## 4. High-Level Protocol

The recommended high-level command format is a newline-terminated text line, consistent with the existing communication style:

```text
hl:{"action":"collect","object":"apple"}
hl:{"action":"goto","target":"visible_object","object":"apple"}
hl:{"action":"stop"}
hl:{"action":"look","direction":"left"}
```

Commands without the `hl:` prefix can remain a compatibility mode for direct serial commands:

```text
wrist horizontal 80
shoulder horizontal 40
wheels fb 15
```

GabotApp must clearly separate both modes:

| Input | Processing |
|---|---|
| `hl:{...}` | Interpreted in GabotApp, planned, expanded into serial command sequences |
| Other text | Direct serial command, validated and forwarded to Arduino |

## 5. Proposed GabotApp Modules

### BluetoothCommandGateway

Builds on the existing `BluetoothServerManager`. It receives text lines, sends responses, and maintains the connection to Phone No. 2.

### HighLevelCommandInterpreter

Parses high-level commands with the `hl:` prefix. Its output is a typed command representation, for example:

```kotlin
sealed interface HighLevelCommand {
    data class Collect(val objectName: String) : HighLevelCommand
    data class GoTo(val target: String, val objectName: String?) : HighLevelCommand
    data class Look(val direction: Direction) : HighLevelCommand
    data object Stop : HighLevelCommand
}
```

### VisionModule

Uses the camera on Phone No. 1. The first version can expose only coarse information:

| Output | Description |
|---|---|
| `objectVisible` | Whether the requested object is visible |
| `centerX` | Horizontal object position in the frame |
| `centerY` | Vertical object position in the frame |
| `confidence` | Detection confidence |

CameraX is a suitable starting point for image acquisition and frame analysis. ML Kit or TensorFlow Lite can be added later.

### RobotState

Holds the state needed for decisions:

| State | Source |
|---|---|
| Serial connection | `SerialInterface.isConnected` |
| Last Arduino response | Serial RX |
| Bluetooth client | `BluetoothServerManager.hasClientConnection()` |
| Visible object | `VisionModule` |
| Current plan step | `CommandPlanner` |

### CommandPlanner

Translates a high-level command and camera state into a sequence of low-level commands. The first implementation should be deterministic and simple.

Example for centering an object:

| Condition | Serial command |
|---|---|
| Object is left | `wheels rl -15` or `shoulder horizontal -40` |
| Object is right | `wheels rl 15` or `shoulder horizontal 40` |
| Object is centered | `wheels rl 0` |
| Object is not visible | `wheels rl 15` for a short time, then evaluate the camera again |

Example for `hl:{"action":"stop"}`:

```text
shoulder horizontal 0
shoulder vertical 0
wheels fb 0
wheels rl 0
grab 0
release 0
```

### SerialCommandExecutor

Wraps the existing `SerialInterface`. It handles sequential command sending, response waiting, timeouts, and stopping on error.

Minimum rules:

| Rule | Reason |
|---|---|
| Send one command at a time | Firmware returns simple line-based responses |
| Wait for `OK` or `ERR` | The planner must know whether it can continue |
| Stop the plan on `ERR` | Robot safety |
| Send stop commands on timeout | Safe fail-stop behavior |

## 6. Changes in the Current Application

Current `MainActivity.onMessageReceived()` forwards every Bluetooth message directly to serial:

```kotlin
manager.send("$message\n")
```

Target behavior:

```kotlin
if (message.startsWith("hl:")) {
    highLevelController.handle(message)
} else {
    serialCommandExecutor.sendDirect(message)
}
```

Recommended new classes:

| File | Purpose |
|---|---|
| `HighLevelCommand.kt` | Data model for high-level commands |
| `HighLevelCommandParser.kt` | Parser for `hl:` JSON commands |
| `VisionModule.kt` | Camera and image analysis |
| `RobotState.kt` | Shared decision-making state |
| `CommandPlanner.kt` | Translation from high-level commands to serial sequences |
| `SerialCommandExecutor.kt` | Controlled serial command sending and response waiting |
| `HighLevelController.kt` | Orchestration of parser, camera, planner, and serial executor |

## 7. Safety

Safety rules must be part of GabotApp, not only the firmware:

1. Stop wheels and shoulder when the Bluetooth connection is lost.
2. Abort the active plan when the serial connection is lost.
3. Do not continue autonomous movement when the camera fails.
4. On `hl:{"action":"stop"}`, cancel the command queue immediately and send the stop sequence.
5. Do not send the next motion command until the previous command has completed or has been stopped.

> **Fail-stop rule:** Any loss of Bluetooth, serial, camera, command response, or parser validity must end in a stop sequence before the system accepts another motion plan.

## 8. Testing

### Unit Tests

| Module | Tests |
|---|---|
| `HighLevelCommandParser` | valid JSON, invalid JSON, unknown actions |
| `CommandPlanner` | object left/right/centered, object not visible |
| `SerialCommandExecutor` | OK sequence, ERR failure, timeout |
| `HighLevelController` | `hl:stop`, serial disconnected, camera unavailable |

### Integration Tests

The existing black-box serial protocol tests in `tests/firmware` remain important. They verify real USB serial communication with the firmware. For Variant 2, Android tests should be added with a mock `SerialInterface` and mock `VisionModule`.

### Manual Tests

1. Connect the Arduino to Phone No. 1 over USB OTG.
2. Start GabotApp and verify serial connected.
3. Connect Phone No. 2 over Bluetooth.
4. Send `hl:{"action":"stop"}` and verify stop commands.
5. Send a simple high-level command with no camera detection and verify safe behavior.
6. Send a high-level command with a visible object and verify the gradual motion sequence.

## 9. Implementation Phases

### Phase 1 - Split Bluetooth Input

Add handling for `hl:` vs. direct serial commands. Keep direct serial mode for service and testing.

### Phase 2 - Parser and Stop Action

Implement the parser and `hl:{"action":"stop"}`. This validates the full flow without camera logic.

### Phase 3 - SerialCommandExecutor

Add command queueing, `OK/ERR` waiting, timeouts, and fail-stop behavior.

### Phase 4 - Camera on Phone No. 1

Add CameraX preview/analysis and the first `VisionModule`.

### Phase 5 - CommandPlanner

Implement simple deterministic planning based on object position in the camera frame.

### Phase 6 - Tests and Robot Tuning

Extend Android unit tests and manually verify the sequences on the real robot.

## 10. Open Decisions

| Question | Proposal |
|---|---|
| High-level command format | Keep `hl:` + JSON |
| Direct serial mode | Keep it for service and testing |
| Camera | Use CameraX as the base |
| Object detection | Start simple; add ML Kit/TFLite later |
| Planning | Deterministic rules before an AI planner |
| Safety | Fail-stop on BT, serial, camera, or timeout errors |

---

OpenSourceRobot / GABOT  |  Variant 2 Design Proposal  |  2026
