# GabotPcClient

Kotlin/Compose Desktop client for controlling GABOT through GabotApp. The UI supports mouse and touch input and uses the same serial commands as the Android GabotClient.

## Bluetooth connection

GabotApp exposes a Bluetooth Classic RFCOMM/SPP service. Desktop Java does not provide one portable RFCOMM API for both Windows and Linux, so GabotPcClient connects through the Bluetooth serial port created by the operating system.

1. Start GabotApp on the phone connected to GABOT by USB.
2. Pair the PC with that phone in the Windows or Linux Bluetooth settings.
3. Create or select the outgoing Bluetooth serial port for GabotApp.
4. Start GabotPcClient, choose the corresponding COM or `/dev/rfcomm*` port and press **Connect**.

On Linux, the user must have read/write access to the selected serial port. Ubuntu normally grants this through the `dialout` group:

```bash
sudo usermod -aG dialout "$USER"
```

Log out and back in after changing group membership. Depending on the desktop Bluetooth manager, an RFCOMM port can also be bound manually with `rfcomm`.

## Build and run

The project reuses the Gradle wrapper stored in `gabot/client`:

```bash
cd gabot/pc_client
./gradlew run
./gradlew test
./gradlew build
```

Create a native package for the current operating system:

```bash
./gradlew packageDistributionForCurrentOS
```

Native packaging requires JDK 21 with `jpackage`. Linux produces a DEB package; Windows produces an MSI package.
On Windows, set `JAVA_HOME` to the JDK 21 installation before running `gradlew.bat`.

## Application log

GabotPcClient writes every UI log entry to `gabot-pc-client.log`. A user-space installation stores the file beside the application launcher. A system installation under `/usr` or `/opt` stores it at `/var/log/gabot-pc-client.log`. The active log rotates at 5 MiB and retains five archives named `.1` through `.5`, where `.1` is the newest.

The user running a system installation must have write access to the system log file. It can be prepared on Linux with:

```bash
for suffix in "" .1 .2 .3 .4 .5; do
    sudo install -o "$USER" -g adm -m 664 /dev/null "/var/log/gabot-pc-client.log${suffix}"
done
```

If the system log is not writable, the application reports a warning and falls back to `gabot-pc-client.log` in its current working directory.
