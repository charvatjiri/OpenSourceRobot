import pytest


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("grab 0", "OK grab 0"),
        ("grab 1", "OK grab 1"),
        ("grab 128", "OK grab 128"),
        ("grab 255", "OK grab 255"),
    ],
    ids=[
        "grab-min",
        "grab-low",
        "grab-mid",
        "grab-max",
    ],
)
def test_grab_valid_values(command: str, expected: str, serial_session) -> None:
    assert serial_session.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("grab -1", "ERR: grab value out of range"),
        ("grab 256", "ERR: grab value out of range"),
    ],
    ids=[
        "grab-below-range",
        "grab-above-range",
    ],
)
def test_grab_out_of_range(command: str, expected: str, serial_session) -> None:
    assert serial_session.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("release 0", "OK release 0"),
        ("release 1", "OK release 1"),
        ("release 128", "OK release 128"),
        ("release 255", "OK release 255"),
    ],
    ids=[
        "release-min",
        "release-low",
        "release-mid",
        "release-max",
    ],
)
def test_release_valid_values(command: str, expected: str, serial_session) -> None:
    assert serial_session.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("release -1", "ERR: grab value out of range"),
        ("release 256", "ERR: grab value out of range"),
    ],
    ids=[
        "release-below-range",
        "release-above-range",
    ],
)
def test_release_out_of_range(command: str, expected: str, serial_session) -> None:
    assert serial_session.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("motor f 45", "OK motor 45"),
        ("motor f 90", "OK motor 90"),
        ("motor f 135", "OK motor 135"),
    ],
    ids=[
        "motor-f-lower-midpoint",
        "motor-f-center",
        "motor-f-upper-midpoint",
    ],
)
def test_motor_f_positions(command: str, expected: str, motor_f_servo) -> None:
    assert motor_f_servo.command(command) == expected
