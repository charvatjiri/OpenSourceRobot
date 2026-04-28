import time


def test_wheels_fb_speed_sequence(wheels_fb_motor) -> None:
    assert wheels_fb_motor.command("wheels fb 15") == "OK wheels fb 15"
    time.sleep(1)
    assert wheels_fb_motor.command("wheels fb 0") == "OK wheels fb 0"
    time.sleep(1)
    assert wheels_fb_motor.command("wheels fb -15") == "OK wheels fb -15"
    time.sleep(1)
    assert wheels_fb_motor.command("wheels fb 0") == "OK wheels fb 0"


def test_wheels_rl_speed_sequence(wheels_rl_motor) -> None:
    assert wheels_rl_motor.command("wheels rl 15") == "OK wheels rl 15"
    time.sleep(1)
    assert wheels_rl_motor.command("wheels rl 0") == "OK wheels rl 0"
    time.sleep(1)
    assert wheels_rl_motor.command("wheels rl -15") == "OK wheels rl -15"
    time.sleep(1)
    assert wheels_rl_motor.command("wheels rl 0") == "OK wheels rl 0"
