import pytest


@pytest.mark.parametrize(
    ("command", "expected", "terminator"),
    [
        ("shoulder horizontal -255", "OK shoulder horizontal -255", "\n"),
        ("shoulder horizontal -80", "OK shoulder horizontal -80", "\n"),
        ("shoulder horizontal 0", "OK shoulder horizontal 0", "\r"),
        ("shoulder horizontal 80", "OK shoulder horizontal 80", "\n"),
        ("shoulder horizontal 255", "OK shoulder horizontal 255", "\n"),
    ],
    ids=[
        "shoulder-horizontal-west-max",
        "shoulder-horizontal-west-mid",
        "shoulder-horizontal-stop",
        "shoulder-horizontal-east-mid",
        "shoulder-horizontal-east-max",
    ],
)
def test_shoulder_horizontal_directions(
    command: str,
    expected: str,
    terminator: str,
    shoulder_horizontal_motor,
) -> None:
    assert shoulder_horizontal_motor.command(command, terminator=terminator) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        (
            "shoulder horizontal -256",
            "ERR: shoulder horizontal value out of range (-255..255)",
        ),
        (
            "shoulder horizontal 256",
            "ERR: shoulder horizontal value out of range (-255..255)",
        ),
        (
            "shoulder horizontal 999",
            "ERR: shoulder horizontal value out of range (-255..255)",
        ),
    ],
    ids=[
        "shoulder-horizontal-below-range",
        "shoulder-horizontal-above-range",
        "shoulder-horizontal-far-above-range",
    ],
)
def test_shoulder_horizontal_out_of_range(
    command: str, expected: str, shoulder_horizontal_motor
) -> None:
    assert shoulder_horizontal_motor.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("shoulder vertical -255", "OK shoulder vertical -255"),
        ("shoulder vertical -80", "OK shoulder vertical -80"),
        ("shoulder vertical 0", "OK shoulder vertical 0"),
        ("shoulder vertical 80", "OK shoulder vertical 80"),
        ("shoulder vertical 255", "OK shoulder vertical 255"),
    ],
    ids=[
        "shoulder-vertical-down-max",
        "shoulder-vertical-down-mid",
        "shoulder-vertical-stop",
        "shoulder-vertical-up-mid",
        "shoulder-vertical-up-max",
    ],
)
def test_shoulder_vertical_directions(
    command: str, expected: str, shoulder_vertical_motor
) -> None:
    assert shoulder_vertical_motor.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        (
            "shoulder vertical -256",
            "ERR: shoulder vertical value out of range (-255..255)",
        ),
        (
            "shoulder vertical 256",
            "ERR: shoulder vertical value out of range (-255..255)",
        ),
        (
            "shoulder vertical -999",
            "ERR: shoulder vertical value out of range (-255..255)",
        ),
    ],
    ids=[
        "shoulder-vertical-below-range",
        "shoulder-vertical-above-range",
        "shoulder-vertical-far-below-range",
    ],
)
def test_shoulder_vertical_out_of_range(
    command: str, expected: str, shoulder_vertical_motor
) -> None:
    assert shoulder_vertical_motor.command(command) == expected
