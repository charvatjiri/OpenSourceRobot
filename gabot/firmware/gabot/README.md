# GABOT Firmware

Firmware for the GABOT robot arm on wheels, running on **Arduino Mega**.

## Overview

GABOT is a mobile robot with a shoulder arm, wrist servos, and finger gripper, controlled wirelessly via an NRF24L01 radio module or locally via the serial line (115200 baud).

### Modules

| Module                 | Description                                           |
|------------------------|-------------------------------------------------------|
| `gabot.ino`            | Main sketch — setup, radio loop, motor control        |
| `Radio`                | NRF24L01 radio communication (channel 120, 1 Mbps)    |
| `Fingers`              | H-bridge finger gripper with overcurrent & timeout    |
| `SerialCommand`        | Serial line command parser                            |
| `AngleSensor`          | AS5600 magnetic encoder for shoulder angle limits     |
| `OvercurrentProtection`| Current monitoring for L, R, UD, WE motors (0.5 A)   |
| `BatteryMonitor`       | Battery voltage check (low < 10 V)                    |

### Safety Features

- **Overcurrent protection** — motors L, R, UD, WE are stopped for 2 s when current exceeds 0.5 A for more than 400 ms.
- **Shoulder angle limits** — AS5600 sensor stops shoulder at ±200° (raw ±2000).
- **Finger timeout** — grab/release motor stops automatically after 3.3 s.
- **Finger overcurrent** — finger motor stops on two consecutive overcurrent readings.
- **Battery monitoring** — long beep on startup if battery is below 10 V.
- **Radio watchdog** — short beep and radio restart if no signal for ~0.5 s.
- **WDT** — 120 ms hardware watchdog resets the board on firmware hang.

---

## Radio Controller

The robot receives 2-byte packets from an NRF24L01 transmitter:

| Byte 0 (low nibble) | Byte 1         | Function                                      |
|----------------------|----------------|-----------------------------------------------|
| `0`                  | -127 … +127    | **Wrist rotate** (servo C) — left / right     |
| `1`                  | -127 … +127    | **Wrist tilt** (servo H) — down / up          |
| `2`                  | -127 … +127    | **Shoulder horizontal** (East / West)         |
| `3`                  | -127 … +127    | **Shoulder vertical** (Up / Down)             |
| `4`                  | -127 … +127    | **Drive left/right** (turning)                |
| `5`                  | -127 … +127    | **Drive forward/back**                        |
| `10`                 | 0 or 1         | **Grab** — 0 = grab ON, 1 = grab OFF         |
| `11`                 | 0 or 1         | **Release** — 0 = release ON, 1 = release OFF|
| `0x55` / `0x55`      | —              | **Heartbeat** (radio watchdog keep-alive)     |

Elements 7, 8, 9 are received but currently unused.

### Wrist Servo Behavior

Wrist servos (C and H) move incrementally — the joystick value controls the **speed** of movement (higher deflection = faster), not the position directly. Servo range is 0–166 degrees.

### Drive Mixing

The drive joystick (elements 4 and 5) uses differential mixing with smooth acceleration (value changes by 1 per loop). The left motor gets `v + h` and the right motor gets `v - h`.

---

## Serial Line Commands

Connect at **115200 baud**, commands are terminated by `\n` or `\r`.

| Command                        | Description                              | Response          |
|--------------------------------|------------------------------------------|-------------------|
| `get version`                  | Returns firmware version                 | `0.1.0`           |
| `grab <0-255>`                 | Activate finger grab (0 = ON)            | `OK grab <value>` |
| `release <0-255>`              | Activate finger release (0 = ON)         | `OK release <value>` |
| `motor f <position>`           | Set finger servo F position (degrees)    | `OK motor <pos>`  |
| `wrist horizontal <position>`  | Set wrist rotate servo C position (degrees)  | `OK motor <pos>`  |
| `wrist vertical <position>`    | Set wrist tilt servo H position (degrees)    | `OK motor <pos>`  |
| `shoulder horizontal <-255…255>` | Drive shoulder East (+) / West (-) by speed | `OK shoulder horizontal <speed>` |
| `shoulder vertical <-255…255>`   | Drive shoulder Up (+) / Down (-) by speed   | `OK shoulder vertical <speed>`   |

Commands are case-insensitive. Unknown commands return `ERR: unknown command: <cmd>`.

### Examples

```
get version
grab 0
release 0
wrist horizontal 90
wrist vertical 45
motor f 120
shoulder horizontal 100
shoulder horizontal 0
shoulder vertical -80
shoulder vertical 0
```

> **Note:** Shoulder commands set a continuous speed, not a position. Send `0` to stop.

---

## Startup Sequence

1. Serial prints `RESET`.
2. All motor pins set LOW, servos attached (pins 9, 10, 11), wrist H set to 80°.
3. Radio, angle sensor, overcurrent, battery, and finger modules initialized.
4. Battery voltage measured — long beep if below 10 V, short beep otherwise.
5. Serial prints `ready` and the measured battery voltage.

## Hardware

- **Board:** Arduino Mega
- **Radio:** NRF24L01 (CE=49, CS=48, 3.3 V)
- **Angle sensor:** AS5600 (I2C, direction pin 4)
- **Charge voltage:** 12–12.6 V only
