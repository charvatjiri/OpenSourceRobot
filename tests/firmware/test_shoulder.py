import time


def test_shoulder_horizontal_speed_sequence(shoulder_horizontal_motor) -> None:
    assert (
        shoulder_horizontal_motor.command("shoulder horizontal 40")
        == "OK shoulder horizontal 40"
    )
    time.sleep(1)
    assert (
        shoulder_horizontal_motor.command("shoulder horizontal -40")
        == "OK shoulder horizontal -40"
    )
    time.sleep(1)
    assert (
        shoulder_horizontal_motor.command("shoulder horizontal 0")
        == "OK shoulder horizontal 0"
    )


def test_shoulder_vertical_speed_sequence(shoulder_vertical_motor) -> None:
    assert (
        shoulder_vertical_motor.command("shoulder vertical -50")
        == "OK shoulder vertical -50"
    )
    time.sleep(1)
    assert (
        shoulder_vertical_motor.command("shoulder vertical 30")
        == "OK shoulder vertical 30"
    )
    time.sleep(1)
