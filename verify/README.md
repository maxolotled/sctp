# SCTP account verification server

Proves ownership of a Minecraft account for self-service SCTP registration,
without needing the SCTP mod or any cooperation from the real
Snailcraft server. Two independent pieces:

- **`java_server.py`** — a from-scratch, minimal Java Edition server that
  only implements the *login* sequence (handshake, RSA encryption exchange,
  Mojang's `hasJoined` check), then disconnects with a result message. This
  is exactly how a real server would kick someone for being whitelisted/
  banned during login — we just always "kick", with a success or failure
  message. This is genuine cryptographic proof of Mojang/Microsoft account
  ownership; nothing here is spoofable without actually owning the account.
- **`bedrock_bridge.py`** — a much simpler *offline-mode* listener, meant to
  only ever be reached by a local Geyser process (see setup below), which
  does real Xbox Live authentication for Bedrock players before forwarding
  them here. See that file's docstring for the full reasoning — short
  version: we trust Geyser's own auth instead of trying to decode
  Floodgate's internal data format, which avoids a real risk of getting an
  undocumented, version-sensitive wire format subtly wrong.

Neither of these actually runs a game — no world, no chunks, no Play state.
They exist purely to answer "is this really that Minecraft account?" and
report the result back to the SCTP Worker.

## How a player uses this (Java)

1. Website generates a short code and tells them to add a server at
   `<code>.verify.sctp.nl` and join it.
2. Their client connects, we read `<code>` straight out of the server
   address they typed (no in-game typing needed), run the real login
   handshake, and report the result to the Worker.
3. They see a message and get disconnected. Website (polling) picks up the
   result and moves to the next step.

## How a player uses this (Bedrock)

Bedrock has no equivalent of "the address I typed" carried in its handshake,
so there's no code to embed this way. Instead the website has them type
their Bedrock gamertag (we prepend `.` — same convention Floodgate uses on
the real server) and asks them to join a **fixed** address (whatever you
point Geyser's public listener at). Geyser authenticates them via Xbox Live
for real, then forwards them here; we match the resulting username against
whichever pending registration currently claims it.

## Setup

```
pip install -r requirements.txt
export VERIFY_WORKER_SHARED_SECRET="<same value as the Worker's VERIFY_SERVER_SECRET>"
python server.py
```

Environment variables (all optional except the shared secret — see
`config.py`): `VERIFY_JAVA_PORT` (default 25565), `VERIFY_BEDROCK_BRIDGE_PORT`
(default 25567, **must stay bound to 127.0.0.1**), `VERIFY_BEDROCK_PREFIX`
(default `.`), `VERIFY_WORKER_API_BASE`.

DNS: point `*.verify.sctp.nl` (a wildcard record) at this machine's public
IP, so any `<code>.verify.sctp.nl` actually resolves.

### Geyser standalone (for Bedrock)

Download Geyser Standalone from https://geysermc.org/download and run it
on the same machine, with a config roughly like:

```yaml
bedrock:
  address: 0.0.0.0
  port: 19132          # the port Bedrock players actually connect to
remote:
  address: 127.0.0.1
  port: 25567           # must match VERIFY_BEDROCK_BRIDGE_PORT
  auth-type: offline     # important — no Floodgate needed
```

No Floodgate plugin/jar needed for this setup — Geyser's own offline
passthrough is enough, since verification only cares about the
Xbox-authenticated gamertag, not any richer profile data.

## Known limitations (be aware before relying on this)

- The Java path has been tested end-to-end against a real client (real
  Java login, real Mojang hasJoined, real account created via
  sctp.nl/register) — confirmed working, including the 1.20.5+
  "should authenticate" Encryption Request field, sent conditionally based
  on the client's declared protocol version (>= 766).
- The Bedrock path has been tested end-to-end against a real Bedrock client
  through Geyser — confirmed working (real Xbox Live auth via Geyser, real
  account created via sctp.nl/register).
- The Bedrock path's security boundary is entirely "nothing but Geyser can
  reach the bridge port" — if you ever expose `VERIFY_BEDROCK_BRIDGE_PORT`
  to the internet, that guarantee is gone and anyone could claim any
  Bedrock username. Keep it firewalled to localhost.

## Monitoring / uptime alerts

Two independent layers, because each only catches failures the other can't:

- **External**: a free port monitor (e.g. UptimeRobot) watching
  `verify.sctp.nl:25565` from the internet — catches the whole Pi/router/ISP
  going offline, which nothing running on the Pi itself could ever detect or
  report.
- **Local**: `watchdog.py` — checks that `java_server` (TCP) and Geyser's
  Bedrock listener (RakNet UDP ping) are actually up on the Pi itself, and
  posts to a Discord webhook only when a service's state *changes* (down, or
  back up), not on every check. Catches a crash-loop the Pi survives but
  that an external port check might still see as "up" if something else on
  the port responds, or might be slower to catch depending on check interval.

Run it periodically via a systemd timer:

```
export WATCHDOG_DISCORD_WEBHOOK_URL="<your Discord webhook URL>"
python3 watchdog.py
```

See the deploy notes for the actual timer unit — it needs
`WATCHDOG_DISCORD_WEBHOOK_URL` set (same idea as `VERIFY_WORKER_SHARED_SECRET`,
via the service's `Environment=` line or a wrapper like `run.py`).
