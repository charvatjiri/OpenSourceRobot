import re

import pytest


VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("get version", "3.1.4"),
        ("GET VERSION", "3.1.4"),
        ("version", "3.1.4"),
        ("GeT VeRsIoN", "3.1.4"),
    ],
    ids=[
        "version-lowercase",
        "version-uppercase",
        "version-alias",
        "version-mixed-case",
    ],
)
def test_get_version(command: str, expected: str, serial_session) -> None:
    response = serial_session.command(
        command,
        predicate=lambda line: bool(VERSION_RE.fullmatch(line)),
    )
    assert response == expected


def test_unknown_command(serial_session) -> None:
    assert (
        serial_session.command(
            "blabol xyz",
            predicate=lambda line: line.startswith("ERR: unknown command: "),
        )
        == "ERR: unknown command: blabol xyz"
    )


@pytest.mark.parametrize(
    "command",
    [
        "grab xyz",
        "release 1x",
        "wrist horizontal --1",
        "shoulder vertical 12.5",
        "wheels fb",
    ],
)
def test_invalid_numeric_argument_returns_error(command: str, serial_session) -> None:
    response = serial_session.command(
        command,
        predicate=lambda line: line.startswith("ERR:"),
    )
    assert response.startswith("ERR:")


def test_command_too_long_and_parser_recovers(serial_session) -> None:
    response = serial_session.command(
        "x" * 120,
        predicate=lambda line: line.startswith("ERR:"),
    )
    assert response == "ERR: command too long"
    assert (
        serial_session.command(
            "version",
            predicate=lambda line: bool(VERSION_RE.fullmatch(line)),
        )
        == "3.1.4"
    )
