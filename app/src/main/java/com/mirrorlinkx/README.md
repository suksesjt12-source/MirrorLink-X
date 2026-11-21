# MirrorLink X

Android-to-Android screen sharing prototype.

## Current MVP
- Custom dark UI and MirrorLink X branding
- Local IP display and QR pairing payload
- Android MediaProjection permission request
- Foreground capture service and virtual display lifecycle
- UI adapts to device orientation

## Not implemented yet
This first commit does **not** transmit video/audio over the network, decode a remote stream, scan QR codes, or inject remote touch. The QR currently contains local address information for the planned LAN transport. These features require additional implementation and testing.

## Build
GitHub Actions builds a debug APK. Install only on devices you control and approve screen-capture prompts yourself.
