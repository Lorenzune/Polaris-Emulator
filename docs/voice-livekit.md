# LiveKit voice integration

The client uses LiveKit for all voice media. The emulator remains the authority for room eligibility, vocal bans, and participant state; it signs short-lived LiveKit tokens and never sends the API secret to the browser.

## Emulator configuration

Set these variables in the emulator service environment, then restart the emulator:

```text
LIVEKIT_URL=wss://livekit.ketamini.it
LIVEKIT_API_KEY=<LiveKit API key>
LIVEKIT_API_SECRET=<LiveKit API secret>
```

The URL is the public WSS base URL, without an `/rtc` path. These environment variables override values from the emulator properties file or database. Keep the API secret only on the emulator host; do not add it to client configuration, source control, or browser code. Rotate any key/secret or test token that has been shared outside the server.

## Connection flow

1. On entering a vocal-enabled room, the client connects to LiveKit as a listener. It does not request microphone permission or display a voice badge just for listening.
2. When a user explicitly selects **Attiva microfono**, the browser requests microphone access and applies the selected standard browser capture options and input gain.
3. The client requests a token over the authenticated emulator connection. The emulator verifies the user is in that room and is not vocal-banned, then signs a 15-minute JWT scoped to `habbo-<roomId>` and the user's numeric identity.
4. All room clients subscribe to microphone tracks; only users who enable their microphone publish one and appear in the voice participant badges. LiveKit handles media transport, remote subscriptions, and reconnection; emulator signaling is used for token delivery, active-speaker presence, and moderation.
5. Disabling the microphone stops publishing but keeps the client listening. Leaving the room disconnects LiveKit and removes any active-speaker presence entry.

The access token currently permits room join, audio publishing, and subscribing, but not LiveKit data publishing. Do not use a static LiveKit token in the client.

## DNS, TURN, and firewall

The public LiveKit endpoint must have valid TLS and resolve to the LiveKit server. Configure `turn.ketamini.it` as the TURN hostname in the LiveKit server's settings, point DNS to that server, install a valid certificate for TURN/TLS if enabled, and open the media/TURN ports specified by the deployment's LiveKit configuration at both the VM and provider firewalls. LiveKit advertises its ICE/TURN settings to the browser; the Habbo client does not configure a second independent TURN service.

The WSS connection alone is not enough: WebRTC media UDP (and any configured TCP/TLS fallback) must reach the LiveKit host. Check the generated Docker configuration for the exact ports it selected; the WSS reverse proxy does not carry the media stream.

## Quick checks

- From a browser session in a vocal-enabled room, LiveKit should show one listener participant with identity equal to the Habbo user ID; a microphone track should appear only after the user activates their microphone.
- The LiveKit room name should be `habbo-<roomId>`; different Habbo rooms must not share a LiveKit room.
- If token delivery fails, confirm all three environment variables are present in the emulator process and that its clock is synchronized.
- If signaling connects but audio does not, inspect the browser's ICE candidate pair and verify the configured LiveKit media/TURN ports are reachable; WSS success by itself does not prove media connectivity.
