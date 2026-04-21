import pytest


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("wrist horizontal 42", "OK motor 42"),
        ("wrist horizontal 83", "OK motor 83"),
        ("wrist horizontal 124", "OK motor 124"),
    ],
    ids=[
        "wrist-horizontal-lower-midpoint",
        "wrist-horizontal-center",
        "wrist-horizontal-upper-midpoint",
    ],
)
def test_wrist_horizontal_positions(
    command: str, expected: str, wrist_horizontal_servo
) -> None:
    assert wrist_horizontal_servo.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("wrist vertical 42", "OK motor 42"),
        ("wrist vertical 83", "OK motor 83"),
        ("wrist vertical 124", "OK motor 124"),
    ],
    ids=[
        "wrist-vertical-lower-midpoint",
        "wrist-vertical-center",
        "wrist-vertical-upper-midpoint",
    ],
)
def test_wrist_vertical_positions(
    command: str, expected: str, wrist_vertical_servo
) -> None:
    assert wrist_vertical_servo.command(command) == expected
