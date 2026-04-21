import re

import pytest


VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("get version", "3.1.3"),
        ("GET VERSION", "3.1.3"),
    ],
    ids=[
        "version-lowercase",
        "version-uppercase",
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
