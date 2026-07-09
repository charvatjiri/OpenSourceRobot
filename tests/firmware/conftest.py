import os
import time
import warnings
from collections.abc import Callable

import pytest
import serial


BOOT_MESSAGES = (
    "RESET",
    "ready",
    "WARNING: battery LOW",
)
NOISE_PREFIXES = (
    " angleA =",
    "angleA =",
    "baterry voltage = ",
    "baterry OK: ",
    "Fingers::",
    "Grab releasing...  ",
    "Radio restarted",
    "radio hardware is not responding!!",
    "AS5600 angle sensor not found",
)
SERVO_DEFAULTS = {
    "wrist_horizontal": ("wrist horizontal 80", "OK motor 80"),
    "wrist_vertical": ("wrist vertical 100", "OK motor 100"),
}
TEST_FILE_ORDER = {
    "test_protocol.py": 0,
    "test_gripper.py": 1,
    "test_wrist.py": 2,
    "test_shoulder.py": 3,
    "test_wheels.py": 4,
}
TEST_NAME_ORDER = {
    "test_command_too_long_and_parser_recovers": 0,
    "test_get_version": 1,
    "test_unknown_command": 2,
    "test_invalid_numeric_argument_returns_error": 3,
}


def pytest_addoption(parser: pytest.Parser) -> None:
    parser.addoption(
        "--serial-port",
        action="store",
        default=os.getenv("GABOT_SERIAL_PORT"),
        help="USB serial port for the Arduino Mega, e.g. /dev/ttyUSB0 or COM4.",
    )
    parser.addoption(
        "--serial-baudrate",
        action="store",
        type=int,
        default=int(os.getenv("GABOT_BAUDRATE", "115200")),
        help="Baud rate for the GABOT serial protocol.",
    )
    parser.addoption(
        "--serial-timeout",
        action="store",
        type=float,
        default=float(os.getenv("GABOT_SERIAL_TIMEOUT", "1.0")),
        help="Per-read timeout in seconds.",
    )
    parser.addoption(
        "--serial-boot-wait",
        action="store",
        type=float,
        default=float(os.getenv("GABOT_BOOT_WAIT", "2.5")),
        help="Delay after opening the port so the board can reset and print startup logs.",
    )
    parser.addoption(
        "--serial-command-timeout",
        action="store",
        type=float,
        default=float(os.getenv("GABOT_COMMAND_TIMEOUT", "5.0")),
        help="Maximum time to wait for a command response after sending a line.",
    )


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    items.sort(
        key=lambda item: (
            TEST_FILE_ORDER.get(item.path.name, 999),
            item.path.name,
            TEST_NAME_ORDER.get(item.originalname or item.name, 999),
            item.name,
        )
    )


def _is_noise_line(line: str) -> bool:
    return line in BOOT_MESSAGES or line.startswith(NOISE_PREFIXES)


def _last_command_value(command: str) -> int:
    return int(command.rsplit(maxsplit=1)[1])


def _announce(message: str) -> None:
    # Use explicit progress messages because these tests move real hardware.
    print(f"\n[firmware-test] {message}", flush=True)


def _restore_command(
    serial_session: "SerialProtocolSession",
    command: str,
    expected: str,
    *,
    phase: str,
) -> None:
    position = _last_command_value(command)
    _announce(f"{phase} servo position: {command} (position {position})")
    response = serial_session.command(command)
    assert response == expected


class SerialProtocolSession:
    def __init__(self, port: str, baudrate: int, timeout: float, command_timeout: float) -> None:
        self.poll_interval = min(0.02, max(0.001, timeout / 10))
        self._rx_buffer = bytearray()
        self.serial = serial.Serial(
            port,
            baudrate=baudrate,
            timeout=0,
            write_timeout=timeout,
        )
        self.command_timeout = command_timeout

    def close(self) -> None:
        if self.serial.is_open:
            self.serial.close()

    def drain_startup(self, boot_wait: float) -> list[str]:
        time.sleep(boot_wait)
        return self.read_available_lines()

    def read_available_lines(self) -> list[str]:
        lines: list[str] = []
        while True:
            line = self._read_line_until(time.monotonic() + self.poll_interval)
            if line is None:
                break
            if line:
                lines.append(line)
        return lines

    def _read_line_until(self, deadline: float) -> str | None:
        while time.monotonic() < deadline:
            newline_index = self._rx_buffer.find(b"\n")
            if newline_index >= 0:
                raw_line = self._rx_buffer[:newline_index]
                del self._rx_buffer[: newline_index + 1]
                return raw_line.decode("utf-8", errors="replace").strip()

            available = self.serial.in_waiting
            if available > 0:
                self._rx_buffer.extend(self.serial.read(available))
                continue

            time.sleep(self.poll_interval)

        return None

    def command(
        self,
        text: str,
        *,
        terminator: str = "\n",
        predicate: Callable[[str], bool] | None = None,
    ) -> str:
        if predicate is None:
            predicate = lambda _: True

        self.serial.reset_input_buffer()
        self._rx_buffer.clear()
        payload = f"{text}{terminator}".encode("utf-8")
        try:
            self.serial.write(payload)
        except serial.SerialTimeoutException as exc:
            raise AssertionError(
                f"Timed out while writing command {text!r} to serial port"
            ) from exc

        deadline = time.monotonic() + self.command_timeout
        observed: list[str] = []
        while time.monotonic() < deadline:
            line = self._read_line_until(deadline)
            if line is None:
                break

            if not line or _is_noise_line(line):
                continue

            observed.append(line)
            if predicate(line):
                return line

        raise AssertionError(
            f"No matching response for command {text!r}. Observed lines: {observed!r}"
        )

    def expect_command(self, text: str, expected: str, message: str) -> None:
        _announce(message)
        response = self.command(text)
        assert response == expected

    def set_servo_position(self, name: str, command: str, expected: str) -> None:
        position = _last_command_value(command)
        self.expect_command(
            command,
            expected,
            f"Setting {name} servo position to {position}",
        )

    def set_motion_speed(
        self,
        name: str,
        command: str,
        expected: str,
        *,
        duration_seconds: float | None = None,
    ) -> None:
        speed = _last_command_value(command)
        if speed == 0:
            direction = "stop"
        elif speed > 0:
            direction = "positive direction"
        else:
            direction = "negative direction"
        duration = "" if duration_seconds is None else f" for {duration_seconds:g} s"
        self.expect_command(
            command,
            expected,
            f"Setting {name} speed to {speed} ({direction}){duration}",
        )


@pytest.fixture(scope="session")
def serial_port(pytestconfig: pytest.Config) -> str:
    port = pytestconfig.getoption("serial_port")
    if not port:
        pytest.skip(
            "Set --serial-port or GABOT_SERIAL_PORT to run firmware integration tests."
        )
    return port


@pytest.fixture(scope="session")
def serial_session(
    serial_port: str, pytestconfig: pytest.Config
) -> SerialProtocolSession:
    session = SerialProtocolSession(
        port=serial_port,
        baudrate=pytestconfig.getoption("serial_baudrate"),
        timeout=pytestconfig.getoption("serial_timeout"),
        command_timeout=pytestconfig.getoption("serial_command_timeout"),
    )
    startup_lines = session.drain_startup(pytestconfig.getoption("serial_boot_wait"))
    unexpected_startup = [line for line in startup_lines if not _is_noise_line(line)]
    if unexpected_startup:
        warnings.warn(
            f"Ignoring unexpected startup lines on serial port {serial_port}: {unexpected_startup!r}",
            RuntimeWarning,
        )

    yield session
    session.close()


@pytest.fixture
def wrist_horizontal_servo(serial_session: SerialProtocolSession):
    _restore_command(
        serial_session, *SERVO_DEFAULTS["wrist_horizontal"], phase="Starting"
    )
    yield serial_session
    _restore_command(
        serial_session, *SERVO_DEFAULTS["wrist_horizontal"], phase="Ending"
    )


@pytest.fixture
def wrist_vertical_servo(serial_session: SerialProtocolSession):
    _restore_command(
        serial_session, *SERVO_DEFAULTS["wrist_vertical"], phase="Starting"
    )
    yield serial_session
    _restore_command(
        serial_session, *SERVO_DEFAULTS["wrist_vertical"], phase="Ending"
    )


@pytest.fixture
def shoulder_horizontal_motor(serial_session: SerialProtocolSession):
    yield serial_session
    assert serial_session.command("shoulder horizontal 0") == "OK shoulder horizontal 0"


@pytest.fixture
def shoulder_vertical_motor(serial_session: SerialProtocolSession):
    yield serial_session
    assert serial_session.command("shoulder vertical 0") == "OK shoulder vertical 0"


@pytest.fixture
def wheels_rl_motor(serial_session: SerialProtocolSession):
    yield serial_session
    assert serial_session.command("wheels rl 0") == "OK wheels rl 0"


@pytest.fixture
def wheels_fb_motor(serial_session: SerialProtocolSession):
    yield serial_session
    assert serial_session.command("wheels fb 0") == "OK wheels fb 0"


@pytest.fixture(autouse=True)
def inter_test_delay():
    yield
    time.sleep(3)
