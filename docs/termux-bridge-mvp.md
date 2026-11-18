# DSHA–Termux Execution Bridge (experimental)

This branch adds an **opt-in, on-demand Termux runner** to DSHA. It uses the official `com.termux.RUN_COMMAND` Intent and receives a private `PendingIntent` callback. The DSH Agent gets `termux_status` and `termux_script` tools. Termux does not need to run a permanent command server.

**Security/MVP scope:** Only scripts deliberately placed by the phone owner in `~/.dsha-bridge/scripts/<name>.sh` can be invoked (short, validated script IDs and up to four short arguments). The Agent cannot submit arbitrary shell source, executable paths or root commands through this tool. Review each script before installing it. The runtime still inherits Termux-user permissions.

## Install/authorize on Android

1. Install official Termux (`com.termux`) from F-Droid or its official GitHub project. Initialize it once.
2. In Termux run:

   ```sh
   pkg update
   pkg install bash python
   mkdir -p ~/.termux ~/.dsha-bridge/scripts
   printf '\nallow-external-apps = true\n' >> ~/.termux/termux.properties
   termux-reload-settings
   ```

3. Create `~/.dsha-bridge/scripts/probe.sh`:

   ```sh
   #!/data/data/com.termux/files/usr/bin/bash
   echo "Termux bridge active"
   python --version
   id
   ```

   Run `chmod 700 ~/.dsha-bridge/scripts/probe.sh`.
4. Install the separately signed `.debug` APK from the Actions workflow's artifact, **only after the build passes**. This is a different package from your production DSHA app.
5. Grant the test DSHA app the Android permission **Run commands in Termux environment** in system settings. Permission labels depend on the device. Termux must allow external commands, and background restrictions may still block startup.
6. Ask DSH to call `termux_status`, then `termux_script` with `name: "probe"`. Confirm stdout/stderr/exit code.

## Build

On the feature branch, `.github/workflows/termux-bridge-apk.yml` is configured to run on push and generate a test-signed APK via DSHA's pinned runtime-input and Android SDK actions. Check **Actions** in your fork and enable workflows if GitHub suspended them after forking. The workflow does **not** create a release, does not use your production keystore and does not upload secrets. Each run uses a new signing certificate, so successive test APKs cannot replace each other without uninstalling.

CI requires large fixed upstream runtime archives, the Android toolchain and network access; no claim is made that any build has passed until GitHub Actions actually reports success.

## Limitations

- The native bridge waits at most 25 seconds; timeout/transport loss may mean **execution occurred**. Do not automatically replay an UNKNOWN task.
- Currently there is no persistent job ID, polling or cancellation API; large results may be truncated by Termux or the bridge.
- Only official Termux package identity `com.termux` is targeted.
- The test build should be reviewed before handling private data; do not give the script runner unrestricted root.
- This is not an official DSHA or Termux feature.

Source: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
