# Firmware Serial Protocol Tests

These tests run against a real Arduino Mega connected over USB.

## Ubuntu Linux and Debian-based clones

Use:

```bash
bash tests/firmware/run_tests.sh --serial-port /dev/ttyUSB0 -v
```

The launcher checks whether the system Python can import `pytest` and
`pyserial`. If not, it installs the required distro packages automatically:

```bash
sudo apt-get update
sudo apt-get install -y python3 python3-pytest python3-serial
```

## Windows 10 and 11

Use:

```powershell
powershell -ExecutionPolicy Bypass -File tests/firmware/run_tests.ps1 --serial-port COM3 -v
```

The PowerShell launcher checks whether Python can import `pytest` and
`pyserial`. If not, it installs them for the current user with:

```powershell
py -3 -m pip install --user pytest pyserial
```

If `py` is not available, the script falls back to `python`.

## Direct Manual Run

If dependencies are already installed, you can run:

```bash
python3 -m pytest tests/firmware -v --serial-port /dev/ttyUSB0
```

or on Windows:

```powershell
py -3 -m pytest tests/firmware -v --serial-port COM3
```
