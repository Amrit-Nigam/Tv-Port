# TvPort companion server

A tiny, dependency-free Python LAN server that runs on your **Mac** and feeds the Android TV
dashboard two things it can't get on its own:

1. **Claude Code status** — what Claude is doing in your terminal, in real time
2. **Battery** — for this Mac, your AirPods + case, and your iPhone

It's read-only (never writes anything back to your machine), serves JSON over the LAN with
permissive CORS, and runs on login via a LaunchAgent.

## Endpoints

| Endpoint | Returns |
|---|---|
| `GET /status` | Current Claude Code state (read fresh from `~/.claude/statusbar/state.json`) |
| `GET /events` | The same state as a Server-Sent Events stream (push on change + ~2s heartbeat) |
| `GET /battery` | Battery JSON for Mac / AirPods / iPhone (see below) |
| `GET /battery/phone?level=NN&charging=1` | iPhone push target for an Apple Shortcut |

`/battery` shape:

```json
{
  "mac":     { "level": 62, "charging": true,  "present": true },
  "airpods": { "earbuds": 80, "case": 56, "connected": true },
  "phone":   { "level": 84, "charging": true, "present": true, "source": "shortcut" }
}
```

## Where each number comes from

- **Mac** — `pmset -g batt`
- **AirPods + case** — `system_profiler SPBluetoothDataType` (the same data the macOS Batteries
  widget uses). Levels only; macOS doesn't expose AirPods *charging* state to any CLI.
- **iPhone** — layered, best-available source (a background thread polls the local ones every 30s
  so requests never block):
  1. **USB** via [libimobiledevice](https://libimobiledevice.org) (`ideviceinfo`) — level **and**
     charging, but only while the phone is cabled to the Mac.
  2. **Shortcut push** — an Apple Shortcut on the iPhone POSTs to `/battery/phone`. Most reliable
     *wireless* source: works anywhere on Wi-Fi, carries charging, needs no special permission.
  3. **Continuity hotspot** — the level the Wi-Fi menu shows, parsed from the unified log
     (`SFRemoteHotspotDevice ... battery life: N`). Passive/automatic but only while the phone is
     broadcasting Instant Hotspot near the Mac; no charging.

> **Why not libimobiledevice over Wi-Fi?** It exists, but modern macOS blocks mDNS/Bonjour device
> discovery for background tools (Local Network privacy permission), so a LaunchAgent can't reach
> the phone wirelessly even though the phone advertises itself. The Shortcut sidesteps this.

## Setup

### 1. The server + LaunchAgent
```bash
mkdir -p ~/.claude/statusbar
cp server/serve.py ~/.claude/statusbar/serve.py

# Edit the three paths in the template to match your machine, then:
cp server/com.tvport.companion.plist.template ~/Library/LaunchAgents/com.tvport.companion.plist
launchctl load ~/Library/LaunchAgents/com.tvport.companion.plist

curl http://127.0.0.1:4770/battery   # verify
```
Point the app at your Mac's LAN IP in `secrets.properties` (`CLAUDE_STATUS_URL=http://<ip>:4770/status`).

### 2. (Optional) iPhone battery
Pick whichever you want — they stack:

- **USB** (charging while plugged): `brew install libimobiledevice`, then plug the phone in once,
  tap *Trust*, and run `idevicepair pair`.
- **Wireless via Shortcut** (recommended): make an Apple Shortcut — **Get Battery Level** →
  **Get Contents of URL** `http://<mac-ip>:4770/battery/phone?level=<BatteryLevel>` — and run it from
  Automations (charger connected/disconnected for the bolt, plus an app-open or time trigger to
  refresh the level).
- **Hotspot**: nothing to do — automatic whenever the phone is near the Mac.

### 3. (Optional) AirPods / iPhone-USB tooling
`libimobiledevice` is only needed for the USB iPhone read. AirPods need nothing beyond being
connected to the Mac.

## Notes
- Port is `4770` (edit `PORT` in `serve.py`).
- Reserve a **static IP** for the Mac in your router so the dashboard URL never changes, and keep
  the Mac awake on the same Wi-Fi.
