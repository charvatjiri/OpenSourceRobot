# Analysis of the GABOT Project Architecture and Technologies

**Communication (BT 4.2 / BT 5.x vs. Wi-Fi)  •  Control Logic Variant  •  Testing**

Project: OpenSourceRobot / GABOT  •  Document version: 1.4  •  Date: April 16, 2026

---

# 1.  Introduction and Context

This document evaluates three key decisions for developing the third application of the GABOT robot control system: (A) selecting the wireless technology, Bluetooth vs. Wi-Fi, (B) choosing the architectural variant for splitting the control logic between the two phones, and (C) selecting the most suitable testing approach for all system layers.

## 1.1  Three-Layer System Architecture

The system consists of three layers. The Arduino Mega controls the robot motors over a 115,200 baud serial link. Phone No. 1 is physically attached to the robot, communicates with the Arduino via a USB OTG cable, and can optionally provide GPS. Phone No. 2 (the user phone) captures the surroundings with its own camera, displays the UI for the user, and communicates wirelessly with Phone No. 1.

| Layer | Component | Communication |
| --- | --- | --- |
| Layer 1 – Robot | Arduino Mega + firmware GABOT | Serial link 115,200 baud |
| Layer 2 – Control Phone No. 1 | Android + GabotApp (USB OTG, optional GPS) | USB cable -> Arduino |
| Layer 3 – User Phone No. 2 | Android + GabotClient (camera, UI) | Bluetooth / Wi-Fi -> Phone No. 1 |

*Table 1 – Three-layer architecture of the GABOT control system*

# 2.  Comparison of Wireless Technologies: Bluetooth vs. Wi-Fi

The camera is located exclusively on Phone No. 2 and video is not transmitted; only text commands or user input travel over the wireless channel.

| Criterion | Bluetooth 5.x | Wi-Fi 802.11 n/ac |
| --- | --- | --- |
| Range (open space) | 50–100 m (Class 1) | 50-100 m (Direct) / more with an AP |
| Latency | 3–10 ms (SPP/RFCOMM) | 5-20 ms (Direct)<br>10-50 ms (via AP) |
| Throughput | 2-3 Mbit/s - sufficient for text commands | 54-600+ Mbit/s - oversized for this use case |
| Power consumption | Low - medium | Medium - high |
| Infrastructure | None - direct P2P pairing | None (Direct) or AP |
| Android Kotlin API - complexity | Medium: BluetoothAdapter + RFCOMM | Complex (WifiP2pManager)<br>or TCP + AP |
| Interference | 2.4 GHz | 2.4 or 5 GHz (5 GHz is better) |
| Compatibility with the GABOT protocol | Natural - SPP emulates a serial port | TCP socket - IP management required |

*Table 2 – Bluetooth 5.x vs. Wi-Fi (green = advantage, yellow = neutral, red = disadvantage)*

## 2.1  Evaluation of the Bluetooth 4.2 Variant

For the GABOT scenario, where video is not transmitted and only text commands flow between the phones, the older Bluetooth 4.2 is also relevant. Compared with Bluetooth 5.x, it has less headroom in range and throughput, but it remains functionally sufficient for RFCOMM/SPP communication.

| Criterion | Bluetooth 4.2 | Impact on GABOT |
| --- | --- | --- |
| Traffic type | Bluetooth Classic + BLE, RFCOMM/SPP supported | The same text-based application protocol as BT 5.x can be used |
| Range | Typically lower than BT 5.x, usually tens of meters depending on device class | Usually sufficient for controlling the robot over short to medium distances |
| Throughput | More than sufficient for text messages | No limitation for commands, telemetry, and status messages |
| Latency | Low, suitable for interactive control | No obstacle is expected for manual control or high-level commands |
| Android implementation | The same BluetoothAdapter + RFCOMM model as BT 5.x | No separate architecture or different command format is required |
| Risks | Less performance headroom and higher sensitivity to the quality of the specific phone | Recommended to verify on the target device pair with range and connection-stability tests |

*Table 2a – Evaluation of Bluetooth 4.2 for communication between GABOT mobile devices*

Conclusion: Bluetooth Classic (RFCOMM/SPP) is recommended; Bluetooth 4.2 is a usable and sufficient option for GABOT, while Bluetooth 5.x provides greater operating headroom in range and robustness.

# 3.  Architectural Variants for Splitting the Control Logic

## 3.1  Variant Description

> **Variant 1 – Intelligence on Phone No. 2 (GabotClient)**

Phone No. 2 processes the camera image, interprets user input, and sends finished commands to Phone No. 1, which forwards them unchanged to the Arduino serial port.

```text
  Examples: "grab 0"  /  "shoulder horizontal 80"  /  hl:{"action":"collect","object":"apple"}
```

> **Variant 2 – Intelligence on Phone No. 1 (GabotApp)**

Phone No. 2 forwards raw user input to Phone No. 1, which interprets that input, plans the motion sequence, and issues serial commands for the Arduino.

```text
  Examples: "task: collect apples"  /  "detected: apple at [0.3, 0.7]"
```

## 3.2  Variant Comparison

| Criterion | Variant 1 – Intelligence on Phone No. 2 | Variant 2 – Intelligence on Phone No. 1 |
| --- | --- | --- |
| Camera processing | Local on No. 2 - natural fit | Detection on No. 2, results on No. 1 |
| Motion planning | On Phone No. 2 - finished commands | On Phone No. 1 - AI planner required |
| Load on Phone No. 1 | Minimal - only forwarding to Serial | High - AI inference + control loop |
| Latency from input to movement | Lower - planning on No. 2 | Higher - input -> BT -> planning -> Serial |
| Reaction to camera input without UI | Full - No. 2 sees the scene and plans | Limited - No. 1 does not see the scene |
| Dependence on No. 1 performance | Low - a weaker device is sufficient | High - AI + robot control at the same time |
| Impact on GabotApp | Minimal - add BT reception + forwarding | Major - add an AI planner and interpreter |
| Testability | Easy - logic on No. 2, without the robot | Logic tied to No. 1 with the robot |
| Safety on BT disconnect | Robot stops - safe | Robot stops - safe |
| Recommendation | RECOMMENDED for the primary implementation | Alternative for different HW |

*Table 3 – Comparison of Variant 1 vs. Variant 2 (green = advantage, yellow = disadvantage/tradeoff)*

# 4.  Final Architecture Recommendation

> **Recommended architecture: Variant 1 + Bluetooth Classic (RFCOMM/SPP, BT 4.2 or 5.x)**

Phone No. 2 processes the camera locally, interprets user input, plans the motion sequence, and sends finished commands over Bluetooth. Phone No. 1 acts as a forwarder. If older phones are used, the architecture can also run over Bluetooth 4.2 without changing the application protocol.

| Aspect | Rationale |
| --- | --- |
| Communication | Bluetooth Classic RFCOMM/SPP over BT 4.2 or 5.x - zero infrastructure, simple API, compatibility with the GABOT protocol |
| Architecture | Variant 1 - intelligence on Phone No. 2; minimal impact on GabotApp |
| Camera processing | Local on Phone No. 2 - TensorFlow Lite or ML Kit Object Detection |
| Protocol | Extend GABOT with the `hl:` prefix for high-level commands; existing commands remain compatible |
| GabotApp change | Add BluetoothServerSocket + forward received strings to Serial |

*Table 4 – Summary of the architecture recommendation*

## 4.1  Proposed Extension of the GABOT Protocol

Existing GABOT commands are newline-terminated text strings. The `hl:` prefix is proposed for high-level commands:

```text
  hl:{"action":"collect","object":"apple","radius_m":3}
  hl:{"action":"goto","bearing_deg":45,"distance_m":2}
  hl:{"action":"stop"}
```

Commands without the `hl:` prefix are forwarded directly to Serial - backward compatibility is preserved.

## 4.2  Proposed GabotClient Modules (Phone No. 2)

| Module | Technology / library | Responsibility |
| --- | --- | --- |
| CameraModule | Jetpack CameraX | Image capture, frame analysis |
| VisionModule | TensorFlow Lite / ML Kit | Local object detection and classification |
| CommandPlanner | Custom Kotlin logic | Translate detections + user input into commands |
| BluetoothManager | BluetoothAdapter + RFCOMM BluetoothSocket (compatible with BT 4.2 and 5.x) | Pairing and sending commands to Phone No. 1 |
| GPSConsumer (opt.) | Receive GPS data from Phone No. 1 | Robot localization in the field |
| UI / MainActivity | Jetpack Compose or View | User input, log, connection status |

*Table 5 – Proposed modules of the GabotClient application (Phone No. 2)*

# 5.  Testing - Framework and Strategy Selection

The GABOT project consists of two independent components built with different technologies: C++ firmware for Arduino Mega and an Android application in Kotlin. Each layer has its own optimal testing tools. No single multi-platform framework will cover both layers better than the native tools for each of them.

> **Recommended strategy: native testing tools for each layer separately.**

## 5.1  Overview of Test Layers and Recommended Frameworks

| Layer | Framework / library | What it covers | Priority |
| --- | --- | --- | --- |
| Firmware - unit tests<br>(Arduino C++) | AUnit<br>(Arduino port of Unity) | Unit tests of modules directly on the Arduino: SerialCommand, Fingers, AngleSensor, BatteryMonitor, OvercurrentProtection | 1 - highest |
| Firmware - protocol<br>(integration, on PC) | pytest + pyserial<br>(Python) | Black-box tests of the GABOT serial protocol over USB - input/output without needing mocks | 1 - highest |
| Android - unit tests<br>(GabotApp, GabotClient) | JUnit 4 + Mockito<br>(already in the project) | SerialInterface logic, CommandPlanner, command parsing - without devices | 2 - medium |
| Android - UI tests<br>(GabotApp, GabotClient) | Espresso<br>(already in the project) | UI flow: connect, send command, display response - on an emulator or device | 2 - medium |
| Integration<br>end-to-end | pytest + pyserial<br>+ ADB (Python) | Entire chain: command in UI -> BT 4.2/5.x -> Serial -> Arduino -> response | 3 - extension |

*Table 6 – Test layers and recommended frameworks (priority 1 = implement first)*

## 5.2  Firmware - AUnit (unit tests on Arduino)

AUnit is a testing framework for Arduino written in C++, inspired by Google Test. The tests are compiled as part of the firmware sketch and run directly on the Arduino Mega - the results are printed to the serial port (115,200 baud). The library is available in Arduino Library Manager or via PlatformIO.

Main advantages for GABOT: tests run on real hardware, so they validate exactly what will run in production - no mocking environment for AVR peripherals. Individual modules (SerialCommand, Fingers, AngleSensor...) can be tested in isolation by stubbing their dependencies.

### Example test for the SerialCommand module

```text
#include <AUnit.h>
#include "SerialCommand.h"
#include "Fingers.h"

test(parseGrabValid) {
  // Stub: Serial contains 'grab 0\n'
  assertEqual(SerialCmd_Success, GabotSerial.processCommand("grab 0"));
}

test(parseUnknownCommand) {
  assertEqual(SerialCmd_Error, GabotSerial.processCommand("blabol"));
}

test(shoulderHorizontalOutOfRange) {
  assertEqual(SerialCmd_Error, GabotSerial.processCommand("shoulder horizontal 999"));
}

void setup() { Serial.begin(115200); }
void loop()  { aunit::TestRunner::run(); }
```

Installation via Arduino Library Manager: search for 'AUnit' by Brian T. Park.

## 5.3  Firmware - pytest + pyserial (black-box protocol tests)

pytest with the pyserial library opens the USB port and tests the GABOT protocol as a black box - it sends text commands and verifies the Arduino responses. The tests run on a PC, require no special infrastructure, and are understandable even without C++ knowledge.

Advantage over AUnit: the tests are independent of the firmware code, serve as a protocol specification, and are easy to extend.

### Example (tests/firmware/test_serial_protocol.py)

```text
import serial, pytest, time

PORT = '/dev/ttyUSB0'

@pytest.fixture(scope='module')
def ser():
    s = serial.Serial(PORT, 115200, timeout=1)
    time.sleep(2)  # Arduino reset
    s.read_all()   # flush startup messages
    yield s
    s.close()

def cmd(s, text):
    s.write((text + '\n').encode())
    return s.readline().decode().strip()

def test_get_version(ser):
    r = cmd(ser, 'get version')
    assert r.count('.') == 2

def test_grab_valid(ser):
    assert cmd(ser, 'grab 0') == 'OK grab 0'

def test_grab_out_of_range(ser):
    assert cmd(ser, 'grab 256').startswith('ERR')

def test_shoulder_horizontal_valid(ser):
    assert cmd(ser, 'shoulder horizontal 100') == 'OK shoulder horizontal 100'

def test_shoulder_horizontal_stop(ser):
    assert cmd(ser, 'shoulder horizontal 0') == 'OK shoulder horizontal 0'

def test_shoulder_horizontal_out_of_range(ser):
    assert cmd(ser, 'shoulder horizontal 999').startswith('ERR')

def test_wrist_horizontal(ser):
    assert cmd(ser, 'wrist horizontal 90') == 'OK motor 90'

def test_unknown_command(ser):
    assert cmd(ser, 'blabol xyz').startswith('ERR: unknown command')
```

### Run

```text
  pip install pytest pyserial
  pytest tests/firmware/ -v
  pytest tests/firmware/ -v -k 'shoulder'   # run only shoulder tests
```

## 5.4  Android Application - JUnit 4 + Mockito + Espresso

These frameworks are already configured in the project (`build.gradle.kts`, `libs.versions.toml`). There is no need to add new dependencies - only extend the existing test files.

| Framework | Test type | Examples for GABOT |
| --- | --- | --- |
| JUnit 4 | Unit tests (without devices)<br>run on the JVM on a PC | Command parsing in CommandPlanner, BluetoothManager logic (with a mock socket), connection state machine for BT 4.2/5.x |
| Mockito | Dependency mocking<br>for unit tests | Mock BluetoothSocket - test sending without a real BT device; mock SerialInterface for testing UI logic |
| Espresso | Instrumented UI tests<br>on device / emulator | Tap Connect -> display statusText 'Connected'; send command -> display it in the log; behavior on connection loss |

*Table 7 – Android testing frameworks (everything is already configured in the project)*

### Example JUnit + Mockito unit test (CommandPlannerTest.kt)

```text
class CommandPlannerTest {
    private val mockBt = mock(SerialInterface::class.java)

    @Test fun `collect apple sends hl command`() {
        val planner = CommandPlanner(mockBt)
        planner.execute(UserInput.Collect("apple", radiusM = 3))
        verify(mockBt).send("""hl:{"action":"collect","object":"apple","radius_m":3}\n""")
    }

    @Test fun `unknown task does not send command`() {
        val planner = CommandPlanner(mockBt)
        planner.execute(UserInput.Unknown)
        verifyNoInteractions(mockBt)
    }
}
```

### Run

```text
  ./gradlew test                                    # unit tests (JVM)
  ./gradlew connectedAndroidTest                    # Espresso UI tests (device)
  ./gradlew :app:testDebugUnitTest --tests "com.gabotapp.CommandPlannerTest"
```

## 5.5  Proposed Test Coverage - GABOT Serial Protocol

| Command | Test case | Expected response |
| --- | --- | --- |
| get version | Version format X.Y.Z | \d+.\d+.\d+ |
| grab 0 | Valid value | OK grab 0 |
| grab 256 | Out of range (max 255) | ERR: ... |
| release 0 | Valid value | OK release 0 |
| wrist horizontal 90 | Valid position for servo C | OK motor 90 |
| wrist vertical 45 | Valid position for servo H | OK motor 45 |
| motor f 120 | Valid position for servo F | OK motor 120 |
| shoulder horizontal 100 | Valid speed (+) | OK shoulder horizontal 100 |
| shoulder horizontal 0 | Stop the arm | OK shoulder horizontal 0 |
| shoulder horizontal 999 | Out of range (-255..255) | ERR: ... |
| shoulder vertical -80 | Negative speed (down) | OK shoulder vertical -80 |
| wheels rl 50 | Wheel turning | OK wheels rl 50 |
| wheels fb -100 | Reverse driving | OK wheels fb -100 |
| xyz unknown | Unknown command | ERR: unknown command: ... |

*Table 8 – Proposed test coverage for the GABOT serial protocol (pytest + pyserial)*

## 5.6  Recommended Test Directory Structure

```text
tests/
  firmware/
    test_serial_protocol.py   # pytest + pyserial - black-box protocol tests
    conftest.py               # shared fixture (Serial port)
    requirements.txt          # pytest, pyserial
  arduino/
    AUnitTests/
      AUnitTests.ino          # AUnit sketch - module unit tests on Arduino
  android/
    (JUnit + Espresso tests are part of the Gradle project in gabot/app/)
```

---

OpenSourceRobot / GABOT  •  Architecture and Testing Analysis  •  v1.4  •  2026
