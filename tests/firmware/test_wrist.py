def test_wrist_horizontal_position_sequence(wrist_horizontal_servo) -> None:
    wrist_horizontal_servo.set_servo_position(
        "wrist horizontal", "wrist horizontal 150", "OK motor 150"
    )
    wrist_horizontal_servo.set_servo_position(
        "wrist horizontal", "wrist horizontal 10", "OK motor 10"
    )
    wrist_horizontal_servo.set_servo_position(
        "wrist horizontal", "wrist horizontal 80", "OK motor 80"
    )


def test_wrist_vertical_position_sequence(wrist_vertical_servo) -> None:
    wrist_vertical_servo.set_servo_position(
        "wrist vertical", "wrist vertical 50", "OK motor 50"
    )
    wrist_vertical_servo.set_servo_position(
        "wrist vertical", "wrist vertical 150", "OK motor 150"
    )
    wrist_vertical_servo.set_servo_position(
        "wrist vertical", "wrist vertical 100", "OK motor 100"
    )
