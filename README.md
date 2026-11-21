# MirrorLink X

MirrorLink X is an Android-to-Android screen sharing prototype over a shared local Wi-Fi network.

## Version 1.3.0

- QR scanner now requests camera permission before opening the scanner and declares the embedded scanner activity explicitly.
- Receiver pairing QR is larger for easier scanning.
- Screen capture through Android MediaProjection with the user's approval.
- LAN video transfer over TCP using JPEG frames (port 4747).
- QR pairing with a random session token.
- Optional app playback-audio capture on Android 10+; it does not record the microphone.
- Remote tap/swipe gestures over Wi-Fi TCP or Bluetooth Classic.
- Bluetooth touch control uses an already-paired phone selected from the receiver's bonded-device list. Pair both phones in Android Bluetooth settings first.
- Touch control uses an Android Accessibility Service that the sender must explicitly enable in Android Settings.
- Receiver screen preview adapts to the available view area.
- GitHub Actions builds a debug APK and uploads it as the `MirrorLink-X-debug` artifact.

## How to use

1. Install the same MirrorLink X debug APK on both Android phones.
2. Connect both phones to the same trusted Wi-Fi network.
3. On the receiving phone, open MirrorLink X and tap **Start receiver**. Keep the larger QR code visible.
4. On the sending phone, tap **Scan receiver QR** and grant camera permission when Android asks.
5. Optionally enable **Include supported app audio**. Android will ask for audio permission. No microphone audio is captured.
6. Optionally enable **Bluetooth touch-control fallback** on the sender. Grant Bluetooth permission, turn Bluetooth on, and pair both phones in Android Bluetooth settings.
7. Tap **Share my screen** and approve Android's screen-capture prompt.
8. The receiver displays the sender screen.

## Remote touch over Wi-Fi

1. Enable **MirrorLink X touch control** in Android Accessibility settings on the sender.
2. Keep both phones on the same Wi-Fi network.
3. The receiver automatically attempts the TCP touch channel when video connects.

## Remote touch over Bluetooth

1. Pair the two phones in Android Bluetooth settings before starting the session.
2. On the sender, enable **Bluetooth touch-control fallback** before tapping **Share my screen**.
3. On the receiver, after screen sharing is active, tap **Connect touch control via Bluetooth**.
4. Select the paired sending phone from the list.
5. Drag on the mirrored screen to send a swipe gesture.

Bluetooth carries touch gestures only. Video and optional audio continue over Wi-Fi. Bluetooth touch control requires Android's BLUETOOTH_CONNECT permission on Android 12+ and the sender's explicitly enabled Accessibility service.

## Build APK

The workflow runs on pushes to `main` or `master`, pull requests, and manual dispatch.

1. Open the repository's **Actions** tab.
2. Open the latest **Android Debug APK** run and wait for the build job to finish successfully.
3. Download the `MirrorLink-X-debug` artifact from that run.
4. Extract the ZIP and install `app-debug.apk` on both phones.

## Current limitations and safety notes

- Video uses JPEG frames, not H.264. Quality, bandwidth use, delay, and battery consumption depend on both devices and the Wi-Fi network.
- Audio is raw PCM over the same local connection. Android may block playback capture for apps that disallow it; protected media may remain silent.
- Audio playback capture requires Android 10+ and does not access the microphone.
- Remote touch is limited to single-pointer tap/swipe gestures. It requires the user to enable the Accessibility service on the sending phone; it does not provide keyboard input or multi-touch.
- Bluetooth touch control requires the phones to be paired in system settings and Bluetooth enabled on both devices. Some phone vendors may restrict background Bluetooth connections.
- The pairing token is checked on both control transports, but the media/control stream is not encrypted. Use only a trusted private network and paired devices you trust.
- Build success does not replace testing on physical devices. OEM Android restrictions, Wi-Fi isolation and app playback-capture policies can affect operation.
