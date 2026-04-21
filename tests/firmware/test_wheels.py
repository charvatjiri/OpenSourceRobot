import pytest


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("wheels rl -127", "OK wheels rl -127"),
        ("wheels rl -50", "OK wheels rl -50"),
        ("wheels rl 0", "OK wheels rl 0"),
        ("wheels rl 50", "OK wheels rl 50"),
        ("wheels rl 127", "OK wheels rl 127"),
    ],
    ids=[
        "wheels-rl-left-max",
        "wheels-rl-left-mid",
        "wheels-rl-stop",
        "wheels-rl-right-mid",
        "wheels-rl-right-max",
    ],
)
def test_wheels_rl_directions(command: str, expected: str, wheels_rl_motor) -> None:
    assert wheels_rl_motor.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("wheels rl -128", "ERR: wheels rl value out of range (-127..127)"),
        ("wheels rl 128", "ERR: wheels rl value out of range (-127..127)"),
    ],
    ids=[
        "wheels-rl-below-range",
        "wheels-rl-above-range",
    ],
)
def test_wheels_rl_out_of_range(command: str, expected: str, wheels_rl_motor) -> None:
    assert wheels_rl_motor.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("wheels fb -127", "OK wheels fb -127"),
        ("wheels fb -100", "OK wheels fb -100"),
        ("wheels fb 0", "OK wheels fb 0"),
        ("wheels fb 100", "OK wheels fb 100"),
        ("wheels fb 127", "OK wheels fb 127"),
    ],
    ids=[
        "wheels-fb-back-max",
        "wheels-fb-back-mid",
        "wheels-fb-stop",
        "wheels-fb-forward-mid",
        "wheels-fb-forward-max",
    ],
)
def test_wheels_fb_directions(command: str, expected: str, wheels_fb_motor) -> None:
    assert wheels_fb_motor.command(command) == expected


@pytest.mark.parametrize(
    ("command", "expected"),
    [
        ("wheels fb -128", "ERR: wheels fb value out of range (-127..127)"),
        ("wheels fb 128", "ERR: wheels fb value out of range (-127..127)"),
    ],
    ids=[
        "wheels-fb-below-range",
        "wheels-fb-above-range",
    ],
)
def test_wheels_fb_out_of_range(command: str, expected: str, wheels_fb_motor) -> None:
    assert wheels_fb_motor.command(command) == expected
