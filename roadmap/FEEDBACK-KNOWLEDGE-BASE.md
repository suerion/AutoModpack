# AutoModpack — User Feedback Knowledge Base

*Compiled 2026-07-06 from three sources: (1) full Discord server scrape — 21,004 messages,
249 support/forum threads (May 2025 → Jul 2026), (2) all 53 open GitHub issues + 2 open PRs,
(3) recently closed issues. Raw data lives in `~/Code/discord-scraper/output/AutoModpack/`
(`THREADS.md` = thread catalog with opening-post excerpts, `parts/` = full transcript in
~150KB chunks, `messages.jsonl` = machine-readable). Re-scrape with
`cd ~/Code/discord-scraper && uv run scrape.py`.*

Themes are ranked by support-load (how much time this actually costs) and cross-referenced
to GitHub and to roadmap items in [ROADMAP-5.0.0.md](ROADMAP-5.0.0.md).

---

## T1 — "Host server error" / networking & proxy setup hell  🔥 #1 by far

**The single biggest support burden.** 60+ Discord threads. Users on zerotier, playit.gg,
exaroton, pterodactyl, fps.ms, tickhosting, feather, play.hosting, seedloaf, gate-lite,
docker, nginx, NeoProtect, Modflared, Cloudflare tunnels all hit variations of
`Host server error. AutoModpack host server is down or server is not configured correctly`.

Root causes seen repeatedly:
- `addressToSend`/`portToSend`/`bindAddress`/`bindPort` semantics are hard; `:-1` in logs
  confuses everyone ("Sending X modpack host address: :-1").
- Hosting providers proxy only the MC port; the second TCP port is paid/unavailable.
- Anti-DDoS / proxies drop the "magic packet" pre-TLS handshake on the shared MC port
  (NeoProtect #307, playit #294-family, Modflared #398).
- Docker/NAT loopback: server can't reach its own advertised address, no self-diagnosis.
- Velocity: works only with the dedicated plugins (by Coio, shipped 2026-05-24), and
  backend `/server` switching cannot re-run the login-phase handshake at all
  (thread "Fabric 26.1.2 Velocity, no Modpack Download", Coio + Suerion analysis).

GitHub: #500 (long-distance timeout), #493 (embedded host intermittent), #494 (velocity
rejoin decode error), #478 (fps.ms DNS resolve fail), #294, #307, #398, #493.
Skidam's own positions from Discord: magic packets exist for mod-compat ("we do crimes —
injecting into minecraft networking I/O"); e4mc now works "and should with essential";
p2p via bundled iroh is the endgame ("no port forwarding… endpoint id is your public
ed25519 key", Oct 2025).

→ Roadmap: **N1 (protocol/proxy rework), N2 (self-diagnosis doctor), N3 (static/HTTP
hosting), N5 (iroh P2P)**.

## T2 — Certificate / fingerprint UX

Second-biggest theme, ~25 threads + recurring in #discussion.
- New users don't know what a fingerprint is or where to find it ("wtf is fingerprint,
  where is it" — Skidam himself, Oct 2025). Some players are *scared* by the verification
  screen ("thinking they will get hacked", #helpdesk 2026-02-09).
- Admins keep asking to disable it; a minority asks to *force* it (2026-05-31 thread).
- certbot flow is too hard for the audience; cert must be valid for **both** the MC server
  address and the host address (Skidam: "trust chain X→Y", Dec 2025), which trips everyone.
- Fingerprint changes on cert renewal; nginx-TLS-offload setups make fingerprint retrieval
  confusing ("how do i get the new fingerprint, tls disabled on server end").
- ~~Idea floated 2026-03-19 (#discussion): skip the TOFU screen on online-mode servers~~ —
  **later rejected as unsound**: online-mode authenticates the client to the server, never
  the server to the client, so the encrypted login channel cannot vouch for server identity
  (see roadmap S1).
- The `address;fingerprint` idea originated 2025-10-15 in "Can't get certificate to work."

GitHub: #488 (fingerprint not saved between sessions — NeoForge), #447 (closed: SSH-like
keys), #445 (closed: Let's Encrypt certs not loading), #160 (present JSON URL / transparency).

→ Roadmap: **S1 (online-mode auto-trust), S2 (DNSSEC TXT), S3 (address-embedded pin),
S4 (admin-sourced pin / bootstrap file), S6 (cert UX polish)**.

## T3 — Client secrets expiring / "Authentication failed"

- Default `secretLifetime` 336 h (14 days); players away >2 weeks get
  `Server error: Authentication failed` at launch with no self-healing hint.
- **Confirmed defect:** the server regenerates and saves ONE secret per player UUID on
  every join (`HandshakeS2CPacket:115`, `SecretsStore.saveHostSecret(uuid, …)`), so
  connecting from a second instance/launcher **invalidates the first instance's secret**
  (Suerion reproduced this repeatedly, Dec 2025–Feb 2026).
- Launch-time update check uses the stored secret; if stale → hard error instead of
  "join the server once to refresh".

GitHub: #343 (PCL2 repeated updates is partly this), Discord threads "AutoModPack Not
Letting Me Join A Server" (204 msgs), "Issue on Client on launching AutoModpack".

→ Roadmap: **S5 (mTLS-style client keys, multi-secret per UUID, sliding expiry)**.

## T4 — Client-side mods & side separation

~20 threads. Users constantly confused about `automodpack/host-modpack/main/mods` vs
server `/mods`; want: personal client mods that survive sync (#501 closed, "independent
client mods", "Removing Mods as a Player"), auto-detection of mod side (#370), per-OS
exclusions (Discord 2026-01-23), removing dead-weight client mods per player.
AutoModpack deleting Fabric API / user mods: #502, #477 (closed), #276 (JiJ dependency
breakage), #460 (nonModpackFilesToDelete resolved too late), #377.

→ Roadmap: **G1 (groups/sets cover optional & client-only), C1 (side auto-detection),
C2 (player-local mods contract)**.

## T5 — Restart loops / corrupted downloads / re-update loops

"Restart loop" threads recur monthly; 1KB / 0-byte corrupted files
(threads: "Subject: Critical Issue… 1KB Corrupted File", "Most mods are 1 KB",
"Possible download issues with host" — where Skidam said *"the logic is fundamentally
flawed, i have the refactor on my list"*, 2026-03-30). Launcher-specific re-update loops
(#343 PCL2). Also #473 (AM deletes itself on repacked-zip first launch).

→ Roadmap: **D1 (download pipeline hardening: verify-before-commit, atomic staging),
D2 (update loop detector)**. Note: nix-store CAS on main (057a414f) is the foundation.

## T6 — Show me what you're installing (trust & transparency)

- #402 Download Preview, #160 present JSON to UI, #170 indicate CF/MR download progress,
  #227 "Download mods ONLY from Modrinth/Curseforge" (malware concern).
- Discord: repeated malware worries; Skidam promised GUI source info (2025-10-03:
  "I will add information to the GUI… reliant on the CurseForge API").
- Current fallback order (Skidam, 2025-07-30): Modrinth → CurseForge → server.
  Lookups are client-side batch hash queries (`FetchManager` + `ModrinthAPI`/`CurseForgeAPI`,
  sha1/murmur).

→ Roadmap: **U1 (pre-download contents screen with links), U2 (server-side link
prefetch into content JSON), U3 (platform-only download mode)**.

## T7 — Multiple modpacks / switching / identity

- #462: same server via two addresses = two full modpack copies + restart (Skidam:
  "automodpack is dumb, in the next version thats going to be fixed", 2026-02-20).
- #480: auto-disable/switch packs by server; #326: switch mods off for vanilla servers.
- Discord (nam + Suerion, Dec 2025/Feb 2026): want pre-launch modpack **selection prompt**
  ("A selection before client start is not available yet") and a pre-download "this server
  uses this modpack" confirmation.
- Skidam design note (2026-05-15): internal random folder name for modpack dir; display
  name only in GUIs.

→ Roadmap: **M1 (modpack identity), M2 (seamless switching + auto-select),
M3 (pre-launch selector)**.

## T8 — Modpack groups / sets (the headline feature)

PR #472 (draft, CI green, now conflicting with main). Design already settled in Discord
(#discussion 2026-02-20 → 2026-04-27 + "UI design by Flagan11" thread):
- Fixed groups only; **selective groups scrapped** (can't track file renames across
  updates — the some-mod-9000x.jar problem).
- **Tags** on groups; client can follow a tag or pick groups; maybe categories.
- Backward compat: default config generates single `main` group ≡ current behavior;
  files live in `automodpack/host-modpack/<group-id>/`.
- Wanted: dependency/incompatibility constraints between groups; server-forced tags;
- UI: *group selector* (tags collapse groups) + *group insider* (inspect files, icons,
  descriptions, MR/CF links — "not buy a cat in a bag"); post-install re-configuration UI.
- Implements #416, #223, #151; supersedes #409, #309. Perf-tier presets use case (Suerion).

→ Roadmap: **G1**.

## T9 — Performance

- Server startup: wildcard scan walks everything (70GB bluemap folder → 5 min boot,
  thread "Having large size folder…", Photon; Dual bisected a regression to the wildcard
  rewrite commit 153cf5d8). Partially improved (beta43 wildcard optimization, hash
  caching in 4.0.3, ModFileCache +40% boot on main).
- #457 long update download (5 min per mod); #451 (closed) high RAM.
- JourneyMap dir with 6,000 PNGs re-checked on every join (Mongoose thread, 154 msgs).
- zstd-jni JPMS clashes: #497 (Not Enough Bandwidth double module), DH/OPAC exports clash
  (Oct 2025), Android needed gzip fallback. Skidam: "get rid of zstd… or use some other
  compression" (2025-08-06).

→ Roadmap: **P1 (scan pruning), P2 (zstd strategy), P3 (transfer perf)**.

## T10 — Loader/MC version management

#290 + monthly Discord asks: update NeoForge/Forge/Fabric loader on clients when server
updates. `syncLoaderVersion` exists (Prism/MultiMC/Pandora only). NeoForge installer
feature requested ("Pls make a neoforge version of the Automodpack installer", 49 msgs).
Server-controlled toggle for syncAutoModpackVersion asked (func_kenobi 2026-05-29 —
Skidam: "good point, you should be able to").

→ Roadmap: **L1 (loader sync expansion), L2 (server-remote client config knobs)**.

## T11 — Config sync semantics

allowEditsInFiles/syncedFiles confusion is constant (#313, #288, #483, force-sync single
file in editable dir, "Force Update Config" command ask, options.txt keybind seeding,
FancyMenu dir sync). Skidam noted the hard part (2026-02-03): can't tell user-modified
configs from mod-generated defaults on first download.

→ Roadmap: **C3 (sync rules revamp + docs), U5 (config editor UI)**.

## T12 — Docs & onboarding

"The instructions are quite vague and confusing", "documentation isn't really clear",
"clearly intended for smarter users" — recurring. Wiki moved to moddedmc.wiki (May 2025).
Beginner-written setup guides circulate in threads (Leonature, Suerion) — signal that the
official quick-start doesn't cover the real flow (esp. client-only mods + fingerprint).

→ Roadmap: **X1 (docs overhaul), N2 (doctor reduces docs need)**.

## Smaller / long-tail
- Sync C→S map folders #433; server-to-server sync #349; patch/diff files #262;
  JVM args remote config #407; restart-free file list #288; waiting music config #153
  (fun); AutoPlug integration #248; packwiz compat #26 (static export covers);
  plugin (Bukkit) version #75 (velocity plugin partly covers); Quilt #168 (regression,
  low demand now); ancient MC versions #446 (decline); Kilt #467 (not us);
  majrusz #268 / acedium / citresewn-neopatcher (third-party incompats);
  macOS `.private` #449 (needs repro); hands rendering #479 (mysterious);
  mod-entry generation refusals #470, "Automodpack refuses to generate entry" (112 msgs).

## Community facts worth remembering
- Top helpers: Suerion (UTC+1) (~2,360 msgs), ?CR★ZY (~2,500), Coio (velocity plugins
  author), Flagan11 (UI design), GrahamKracker, nam (tried implementing groups).
- Discord opened ~May 2025; support volume peaked Oct–Nov 2025 (4.0.0 betas → stable).
- 4.0.x shipped: revamped host config (beta37), redesigned cert screen + wildcard perf +
  gzip fallback (beta43/44), TLS session resumption + globs + magic-packet opt (4.0.2),
  hash caching + HAProxy PROXY protocol (4.0.3), host-modpack priority fix (4.0.5).
