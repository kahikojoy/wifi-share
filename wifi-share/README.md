# WiFi Share (Android 13+ Native Wi-Fi Repeater & Relay)

WiFi Share is a native Kotlin + Jetpack Compose Android utility designed for Android 13 (API 33) through Android 16+ that shares an active upstream Wi-Fi connection without root access and without silently consuming mobile data.

---

## Technical Honesty: What Unrooted Android Permits

Android enforces strict security boundaries around system tethering:

1. **System Internet Tethering (`TetheringManager` / NAT)** requires privileged platform permissions (`TETHER_PRIVILEGED`) reserved for system/OEM apps. Ordinary unrooted third-party apps cannot silently turn on kernel IP masquerading.
2. **`LocalOnlyHotspot` (`WifiManager.startLocalOnlyHotspot`)** is available to unprivileged apps holding `NEARBY_WIFI_DEVICES`, **but the OS intentionally isolates it from internet routing** (devices connected to it can only talk to the host phone locally).
3. **Wi-Fi Direct (`WifiP2pManager.createGroup`)** lets unprivileged apps create a custom-named `DIRECT-WS-...` WPA2 group on Android 10+.
4. **How WiFi Share Bridges Internet Legitimately Without Root**:
   - **Local Relay Mode (`LocalOnlyHotspot` or `Wi-Fi Direct` + `WifiBoundRelayServer`)**: Starts a local HTTP/HTTPS `CONNECT` and optional SOCKS5 TCP relay whose outbound DNS lookups and sockets are bound strictly to Android's upstream `TRANSPORT_WIFI` `Network.socketFactory`. Client devices join via QR code / WPA2 password and set the shown manual HTTP Proxy IP & Port. Because outbound sockets are hardware-bound to the Wi-Fi `Network`, cellular data is physically bypassed when **Wi-Fi-Only Safety Mode** is enabled.
   - **System Hotspot Bridge Guide (STA+AP)**: Uses `WifiManager.isStaApConcurrencySupported` and OEM profiles (Google Pixel, Samsung One UI, Xiaomi/Redmi/POCO HyperOS/MIUI, Tecno/Infinix HiOS/XOS) to guide users through native system Wi-Fi sharing where the phone's chipset and firmware allow it, backed by a live cellular-fallback monitor.

---

## Features & Screens

- **Welcome Screen**: Explains unrooted sharing modes and Android 13+ permission scope (`NEARBY_WIFI_DEVICES` with `neverForLocation`, optional `ACCESS_FINE_LOCATION` for SSID display, `POST_NOTIFICATIONS`).
- **Dashboard**: Real-time sharing timer, engine switcher, Start/Stop button, Wi-Fi-Only Safety Guard banner, upstream Wi-Fi card, active client counter, and live `TrafficStats` / relay byte counters.
- **Wi-Fi Information**: Live SSID/BSSID/frequency/channel/standard (Wi-Fi 4–7), RSSI signal meter, IPv4/Gateway/DNS `LinkProperties`, active interface enumeration, and real Wi-Fi-bound TCP/443 reachability probe.
- **Hotspot Configuration**: Engine selection, custom `DIRECT-WS-*` SSID & WPA2 passphrase generator, HTTP/HTTPS & SOCKS5 relay ports.
- **Connected Devices**: Tracks active proxy relay IP sessions with byte counters, Wi-Fi Direct group clients, safe neighbor lookups, and per-IP relay blocking.
- **QR Code Sharing**: Genuine ZXing-encoded `WIFI:T:WPA;...;;` QR matrix, Android 13+ `EXTRA_IS_SENSITIVE` masked clipboard password copy, and client proxy instructions.
- **Compatibility Checker**: Probes `isStaApConcurrencySupported`, `isStaConcurrencyForLocalOnlyConnectionsSupported`, Wi-Fi Direct, 5 GHz, and 6 GHz capabilities with OEM advisories for **Samsung, Tecno, Infinix, Xiaomi, Redmi, and Pixel**.
- **Troubleshooting**: 5-step live self-diagnostic runner plus Android 13–16 & OEM battery/proxy guides.
- **Settings**: Wi-Fi-Only Safety Mode, Auto-Stop on Wi-Fi Drop, SOCKS5 toggle, `connectedDevice` Foreground Service toggle, Dark Navy / Crisp Light / System theme switcher.

---

