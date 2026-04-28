def test_wrist_horizontal_position_sequence(wrist_horizontal_servo) -> None:
    assert wrist_horizontal_servo.command("wrist horizontal 150") == "OK motor 150"
    assert wrist_horizontal_servo.command("wrist horizontal 10") == "OK motor 10"
    assert wrist_horizontal_servo.command("wrist horizontal 80") == "OK motor 80"


def test_wrist_vertical_position_sequence(wrist_vertical_servo) -> None:
    assert wrist_vertical_servo.command("wrist vertical 50") == "OK motor 50"
    assert wrist_vertical_servo.command("wrist vertical 150") == "OK motor 150"
    assert wrist_vertical_servo.command("wrist vertical 100") == "OK motor 100"
