import time


def test_wheels_fb_speed_sequence(wheels_fb_motor) -> None:
    wheels_fb_motor.set_motion_speed(
        "wheels forward/backward",
        "wheels fb 15",
        "OK wheels fb 15",
        duration_seconds=1,
    )
    time.sleep(1)
    wheels_fb_motor.set_motion_speed(
        "wheels forward/backward",
        "wheels fb 0",
        "OK wheels fb 0",
        duration_seconds=1,
    )
    time.sleep(1)
    wheels_fb_motor.set_motion_speed(
        "wheels forward/backward",
        "wheels fb -15",
        "OK wheels fb -15",
        duration_seconds=1,
    )
    time.sleep(1)
    wheels_fb_motor.set_motion_speed(
        "wheels forward/backward", "wheels fb 0", "OK wheels fb 0"
    )


def test_wheels_rl_speed_sequence(wheels_rl_motor) -> None:
    wheels_rl_motor.set_motion_speed(
        "wheels right/left",
        "wheels rl 15",
        "OK wheels rl 15",
        duration_seconds=1,
    )
    time.sleep(1)
    wheels_rl_motor.set_motion_speed(
        "wheels right/left",
        "wheels rl 0",
        "OK wheels rl 0",
        duration_seconds=1,
    )
    time.sleep(1)
    wheels_rl_motor.set_motion_speed(
        "wheels right/left",
        "wheels rl -15",
        "OK wheels rl -15",
        duration_seconds=1,
    )
    time.sleep(1)
    wheels_rl_motor.set_motion_speed(
        "wheels right/left", "wheels rl 0", "OK wheels rl 0"
    )
