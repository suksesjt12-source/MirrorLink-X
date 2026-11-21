# MirrorLink X
Android LAN screen sharing prototype.

Implemented: MediaProjection capture consent, JPEG frame capture, TCP transport on port 4747, receiver display, QR payload, orientation following.

How to test:
1. Put both devices on the same Wi-Fi.
2. Find the receiver phone's IP in Wi-Fi settings.
3. Open app on receiver and tap Start receiver.
4. On sender, enter receiver IP, tap Share my screen, and approve Android capture.
5. Receiver shows the sender's screen.

Limitations: JPEG rather than H.264, no audio, QR camera scanning, remote touch, encryption, or auto-discovery. Receiver IP currently must be found in system Wi-Fi settings. Stream is unencrypted; use trusted local networks only.
