import time


def test_shoulder_horizontal_speed_sequence(shoulder_horizontal_motor) -> None:
    shoulder_horizontal_motor.set_motion_speed(
        "shoulder horizontal",
        "shoulder horizontal 40",
        "OK shoulder horizontal 40",
        duration_seconds=1,
    )
    time.sleep(1)
    shoulder_horizontal_motor.set_motion_speed(
        "shoulder horizontal",
        "shoulder horizontal -40",
        "OK shoulder horizontal -40",
        duration_seconds=1,
    )
    time.sleep(1)
    shoulder_horizontal_motor.set_motion_speed(
        "shoulder horizontal",
        "shoulder horizontal 0",
        "OK shoulder horizontal 0",
    )


def test_shoulder_vertical_speed_sequence(shoulder_vertical_motor) -> None:
    shoulder_vertical_motor.set_motion_speed(
        "shoulder vertical",
        "shoulder vertical -50",
        "OK shoulder vertical -50",
        duration_seconds=1,
    )
    time.sleep(1)
    shoulder_vertical_motor.set_motion_speed(
        "shoulder vertical",
        "shoulder vertical 30",
        "OK shoulder vertical 30",
        duration_seconds=1,
    )
    time.sleep(1)
