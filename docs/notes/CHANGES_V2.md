# MYRA v2 changes

1. First-run setup wizard (`ui/SetupWizardScreen.kt`): mic permission, call/notification permissions, Gemini API key (paste, "Get free key", test key). Replaces the old PermissionsScreen.
2. Incoming call announce is now Gemini Live only. Robotic offline TTS removed from the call flow; offline = silent. If the session is not live yet but internet is available, Myra connects and announces as soon as Live.
3. Faster connect:
   - Root cause: mic permission granted in setup was never forwarded to the service, so it did not connect until restart. Now forwarded by the wizard, the ViewModel and `connect()`.
   - Memory pre-fetch now runs in parallel with the WebSocket handshake (bounded 1.2s wait).
   - Connect timeout 20s -> 10s, reconnect backoff 1.5s -> 0.6s steps, instant reconnect when internet returns.
4. Cleanup: removed dead `mem0/` adapters, unused `memory/MemoryManager` + `MemoryDatabase`, `.bak` file, stray `app/applet`, old notes moved to `docs/notes/`.
