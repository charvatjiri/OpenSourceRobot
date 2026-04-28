import time


def test_grab_pulse(serial_session) -> None:
    assert serial_session.command("grab 1") == "OK grab 1"
    time.sleep(0.25)
    assert serial_session.command("grab 0") == "OK grab 0"


def test_release_pulse(serial_session) -> None:
    assert serial_session.command("release 1") == "OK release 1"
    time.sleep(0.25)
    assert serial_session.command("release 0") == "OK release 0"
