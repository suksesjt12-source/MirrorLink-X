# MirrorLink X

MirrorLink X is an Android-to-Android screen sharing prototype over a shared local Wi-Fi network.

## Current features

- Screen capture through Android MediaProjection with the user's approval.
- LAN video transfer over TCP using JPEG frames (port 4747).
- QR pairing with a random session token.
- Optional app playback-audio capture on Android 10+; it does not record the microphone.
- Optional remote tap/swipe gestures through a separate TCP control channel (port 4749).
- Touch control uses an Android Accessibility Service that the sender must explicitly enable in Android Settings.
- Receiver screen preview adapts to the available view area.
- GitHub Actions builds a debug APK and uploads it as the `MirrorLink-X-debug` artifact.

## How to use

1. Install the same MirrorLink X debug APK on both Android phones.
2. Connect both phones to the same trusted Wi-Fi network. Guest networks or router client-isolation settings may block device-to-device traffic.
3. On the receiving phone, open MirrorLink X and tap **Start receiver**. Keep the QR code visible.
4. On the sending phone, tap **Scan receiver QR** and scan the code on the receiver.
5. Optionally enable **Include supported app audio**. Android will ask for the required audio-capture permission. No microphone audio is captured.
6. Tap **Share my screen** and approve Android's screen-capture prompt.
7. The receiver displays the sender screen.

### Remote touch

Remote touch is optional and disabled unless the sender enables it:

1. On the sending phone, tap **Open touch-control settings**.
2. Find **MirrorLink X touch control** under installed Accessibility services and enable it only if you intend to use remote gestures.
3. On the receiving phone, tap or drag on the mirrored image to send a tap/swipe gesture to the sender.

The Accessibility service does not read screen contents. It receives only gesture coordinates from the paired session and dispatches the requested gesture. Disable the service in Android Settings when it is no longer needed.

## Build APK

The workflow runs on pushes to `main` or `master`, pull requests, and manual dispatch.

1. Open the repository's **Actions** tab.
2. Open the latest **Android Debug APK** run and wait for the build job to finish successfully.
3. Download the `MirrorLink-X-debug` artifact from that run.
4. Extract the ZIP and install `app-debug.apk` on both phones.

## Current limitations and safety notes

- Video uses JPEG frames, not H.264. Quality, bandwidth use, delay, and battery consumption depend on both devices and the Wi-Fi network.
- Audio is raw PCM over the same local connection. Android may block playback capture for apps that disallow it; protected media may remain silent.
- Audio playback capture requires Android 10+ and the Android audio permission. It does not access the microphone.
- Remote touch is limited to tap/swipe gestures. It requires the user to enable the Accessibility service on the sending phone; it does not provide keyboard input or multi-touch.
- The sender and receiver must be reachable on the same LAN. TCP ports 4747 and 4749 must be available.
- The pairing token prevents accidental unpaired sessions, but the media/control stream is not encrypted. Use only a trusted private network; do not use public or unknown Wi-Fi.
- Build success does not replace testing on physical devices. OEM Android restrictions, Wi-Fi isolation and app playback-capture policies can affect operation.
