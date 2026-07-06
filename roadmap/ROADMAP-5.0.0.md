# AutoModpack 5.0.0 Roadmap

> **North star:** *put it on the server and the client, connect, download, play.
> Everything just works — you play modded Minecraft with your friends with ease.*
> Every item below is judged against that sentence. v5 is the release where the
> "connect" part stops needing a sysadmin and the "download" part stops needing trust
> to be blind.

Sources: [FEEDBACK-KNOWLEDGE-BASE.md](FEEDBACK-KNOWLEDGE-BASE.md) (Discord scrape +
GitHub cross-reference), open PRs #472/#459, and the codebase on `main`.
**Everything already merged since `v4.0.5` ships in 5.0.0** (§0).

Sizes: **S** ≤ 2 days · **M** ≤ 2 weeks · **L** multi-week.
Tiers: **T0** foundations / must land first · **T1** headline, gates 5.0.0-beta ·
**T2** should ship during the beta cycle · **T3** stretch / may slip to 5.1.

---

## 0. Already on `main` (banked for 5.0.0)

- **Nix-store-like CAS file management** (057a414f) + skip-existing downloads — the
  dedup foundation that M1/M2 build on.
- **FileMetadataCache / ModFileCache** (+40% boot), client cache moved JSON → H2 MVStore,
  `isUpdate` perf, hash caching.
- **Download scheduler** fixing "Tail of Death", speedometer rewrite, single shared HTTP
  client, parallel MR/CF fetches with randomized URL order.
- **Protocol version negotiation** (5240e93b) — lets 5.x evolve the wire format safely.
- **MC 26.1 + 26.2 ports** (Fabric + NeoForge), Stonecutter 0.9, unified 26.1 impl.
- **Early-service mods load in place from the modpack folder** on neo/forge (#499) —
  no more copy machinery; Connector/Kotlin/CrashAssistant/Monocle verified.
- **No more client DNS lookups** (f9894c73).
- **AutoTester**: Docker in-game integration test framework (#491) + offline scenarios.
- Velocity marked supported (plugins by Coio: automodpack-velocity + loginphaseproxy).

---

## 1. T0 — Foundations

### F1. Rebase & land PR #459 "Simplify dependency resolution" — **S**
Small, already green historically; unblocks touching the updater without fighting old code.

### F2. Rebase PR #472 "modpack groups" onto main — **M**
1880+/985−, drafted pre-4.0.5-refactors and pre-early-services; `WorkaroundUtil` it
touches was deleted on main. Rebase before anything else grows the conflict. CI +
autotester green as the bar. This is the skeleton of G1.

### F3. Wire-protocol v5 rev — **S/M**
Ride the protocol negotiation (5240e93b): bump once for 5.0, reserving message types for
groups (`ConfigurationMessage` already in #472), content-URL hints (U2), client-pubkey
auth (S5), and update-notify (U4). One breaking bump, not five.

### F4. Autotester scenario expansion — **M** *(parallel, ongoing)*
Add: velocity + loginphaseproxy compose scenario (T1 regression net), nginx-TLS-offload
scenario, groups install/switch scenario, secret-expiry scenario, static-host scenario
(N3). Every T1 feature lands with a scenario or it didn't happen.

---

## 2. T1 — Headline features (gate 5.0.0-beta1)

### G1. Modpack sets (groups + tags) — **L** · implements #416 #223 #151, supersedes #409 #309
The settled design (Discord 2026-02→04, "UI design by Flagan11"):
- **Fixed groups only** — selective-file groups are dead (file-rename tracking is
  unsolvable: the some-mod-9000x.jar problem).
- Server: `automodpack/host-modpack/<group-id>/` per group; per-group config entries
  (display name, description, icon?, **tags**, default-on?, forced?); `main` stays the
  implicit forced group ⇒ zero-config servers behave exactly as 4.x.
- **Tags**: client can follow a whole tag or cherry-pick groups; server can force a tag.
  Decide: single tag per group (simpler) — multiple tags only if a real use case shows up.
- **Group constraints**: `conflictsWith` / `requires` between groups so a selection can
  never produce a broken pack (Skidam's own ask, 2026-01-21).
- Client UI: **group selector** (tags collapse; server defaults pre-selected) →
  **group insider** (files with type icons, MR/CF links via U2 — "don't buy a cat in a
  bag"), plus a post-install screen to change selection later (U5 hosts it; entry point
  also from the multiplayer server entry, per ⊃∩⊃⌾/DarkEarth/GrahamKracker asks —
  browse/adjust optional content without joining).
- Content JSON: per-item `group` field; selection stored per modpack; updater resolves
  union of selected groups.
- Use cases from users: performance tiers (low/med/high PC), optional shaders/Fresh
  Animations bundles, client-only QoL sets, creative-vs-survival variants.

### S. Certificate trust & auth overhaul
The umbrella goal: **the fingerprint screen becomes a rare fallback, not the default
first-join experience** — without weakening the trust model.

- **S1. Online-mode auto-trust — S, biggest UX win/LOC.** Skidam's 2026-03-19 finding:
  when the MC server is online-mode, the login-phase channel is Mojang-authenticated and
  encrypted, so the fingerprint delivered in `HandshakeS2CPacket`'s DataPacket is already
  trustworthy ⇒ auto-pin (TOFU via authenticated channel), skip the screen entirely.
  Show a passive "pinned cert from server ✓" line instead. Offline-mode servers keep
  today's flow. *This alone removes the screen for the majority of servers.*
- **S2. DNSSEC TXT fingerprint — M.** Admin publishes
  `_automodpack.<server-domain>. TXT "v=amp1;fp=<sha256>"`. Client resolves with a
  DNSSEC-validating resolver (dnsjava `ValidatingResolver`, bundled root trust anchor;
  we already stopped relying on platform DNS — f9894c73). Validated ⇒ auto-pin.
  Not validated (no DNSSEC) ⇒ treat as hint only: prefill the screen, still require
  confirm. Server side: `/automodpack fingerprint dns` prints the exact record to paste.
- **S3. Address-embedded pin — S.** `play.example.com:25565;<fp>` accepted anywhere a
  server address is entered (idea born 2025-10-15). Parse & strip in the address-resolution
  mixin before vanilla sees it; pin the fingerprint for that host. Admins hand out one
  copy-pasteable string; works with invite-link-style sharing.
- **S4. Admin-sourced pins & first-boot bootstrap — M.** Two artifacts:
  1. **Seedable known-hosts**: ship `automodpack/known-hosts.json` inside a distributed
     zip/mrpack; client merges it on preload (`.private` stays client-private).
  2. **Bootstrap file**: `/automodpack export bootstrap` writes
     `automodpack-bootstrap.json` = {mc address, host address, fingerprint, modpack name,
     (optional) group defaults}. Drop it (or the mrpack containing just AM + this file)
     into a fresh instance ⇒ on first boot Preload installs the full modpack *before
     first launch* — launch, connect, play. This is the "everything ready on first boot"
     flow and the answer to repack-zip workflows that today cause #473-style breakage.
- **S5. Client auth: from bearer secrets to keys — M.**
  Defects today: one secret per UUID overwritten on every join
  (`SecretsStore.saveHostSecret(uuid,…)` from `HandshakeS2CPacket:115`) ⇒ second
  instance kills the first; 14-day hard expiry surfaces as scary "Authentication failed"
  at launch. Fix in two stages:
  1. *Compat stage (5.0.0-beta1):* allow **multiple live secrets per UUID** (key by
     uuid+secretId, prune by lastUsed), **sliding expiry** (touch timestamp on every
     successful use), default `secretLifetime` 336 h → **2160 h (90 days)**, and demote
     expiry to a friendly "reconnect to the server once to refresh" flow.
  2. *Key stage (during beta):* client generates an ed25519/EC keypair per instance in
     `.private/`; pubkey registered via the (authenticated) login channel; host uses TLS
     client-cert auth (Netty `SslContext` with a pinned-key TrustManager — mTLS without
     any CA). Never expires; revocation = prune by lastSeen or `/automodpack revoke
     <player>`. Bearer secrets remain as fallback for old clients via protocol negotiation.
- **S6. Cert UX polish — S.** `/automodpack fingerprint` prints fingerprint + all three
  pin methods; docs task X1 rewrites the certificate page around "pick one of 4 methods";
  keep the force-manual-verification server option (asked 2026-05-31) as a toggle.

### U. Transparency & download-source trust
- **U1. Pre-download contents screen — M** · #402 #160 #170.
  Before accepting a new/updated modpack: full file list (grouped: mods / configs /
  resource packs / other), per-file source badge (Modrinth / CurseForge / this server),
  clickable project links, sizes, total download size, and what gets **deleted**.
  Diff view on updates (added/changed/removed). This screen replaces the bare
  DangerScreen for unknown packs; when a pack is 100% platform-resolvable (every jar
  found on MR/CF by hash), the warning tone is downgraded accordingly (?CR★ZY's ask).
- **U2. Server-side link prefetch — M.** Server does the MR/CF batch hash lookups once
  at generation time (reuse `FetchManager` logic in `ModpackContent`), embedding optional
  `urls[]` + `projectUrl` per `ModpackContentItem`. Clients use embedded data (no API
  calls on the hot path — faster + works when APIs are rate-limited/blocked) and fall
  back to client-side lookups when fields are absent (old servers) — exactly the
  fallback/backup semantics we want. Cache results server-side keyed by sha1.
- **U3. Platform-only download mode — S/M** · #227.
  Client config `downloadSource: any | platforms-only`: with `platforms-only`, binary
  files (jars) whose hashes aren't found on MR/CF are **not downloaded** — client shows
  which files failed policy and lets the user opt in per-file (via U1 screen) or bail.
  Server counterpart flag to advertise "this pack is 100% platform-resolvable" (modpack
  devs can verify via `/automodpack generate --check-platforms`). Non-binary files
  (configs/scripts) are exempt by default — they're inherently server-authored.
- **U6. Changelog authoring — S.** Custom changelogs exist but managing them is "hell
  for the server manager" (Aresky/nam): support `host-modpack/changelog.md` (or
  per-group), shown in the U1/U4 screens; `/automodpack changelog set <text>` for quick
  notes.
- **U4. Update-available notification — S.** Server regenerates content ⇒ notifies
  online AM clients (play-phase packet) ⇒ small toast + chat line: "Modpack updated —
  applies on next restart". Optional client auto-download in background (files staged
  into CAS, applied on restart) so the restart is instant. Non-intrusive: one toast per
  update, no nag loop.

### M. Multi-modpack lifecycle
- **M1. Modpack identity — M** · #462, "automodpack is dumb" fix.
  Content JSON gains a stable random `modpackId` (generated once server-side; folder name
  = id, display name only in GUIs — Skidam's 2026-05-15 position) plus a separate
  `version` display field, so bumping "MyPack v2 → v3" no longer registers as a brand-new
  modpack (Pink Hoodie's report). Client keys
  `installedModpacks` by id, mapping *many* addresses → one installation. Same server
  reachable via two IPs/domains ⇒ one copy, zero re-download (CAS already dedups blobs;
  this dedups the modpack). Migration: match by content-hash on first 5.0 boot.
- **M2. Seamless switching & auto-select — M** · #480 #326.
  On join: if the server's modpackId is installed ⇒ switch selection automatically
  (restart only if actually required — with groups, often not). On joining a server
  without AM ⇒ offer "play vanilla profile" (mods stay in modpack folder, nothing to
  clean). On disconnect from A and connect to B: no manual config edits, ever.
- **M3. Pre-launch modpack selector — S/M** (nam/Suerion ask).
  Optional (`promptModpackOnLaunch`): tiny preload window (we already own a pre-MC
  window on neo/forge; fabric equivalent via the existing preload screen) listing
  installed modpacks — pick one before the game assembles mods. Also solves "I want to
  boot the pack without joining the server" and singleplayer use of a server pack.

### N. Networking that survives the real internet
- **N1. Proxy-transparent protocol — L** · T1 theme, #294 #307 #398 #500 #494.
  Redesign the client↔host negotiation so *nothing non-Minecraft touches the wire
  before TLS*:
  - Move in-game negotiation to **configuration-phase payloads** on 1.20.2+ (proxies and
    velocity pass them; login-phase only kept for ≤1.20.1) — this is also what makes
    velocity backend-switching re-negotiation possible (today impossible; Coio's
    loginphaseproxy exists only because of login-phase).
  - Shared-port detection: replace magic pre-TLS packets with **TLS-first + ALPN**
    (`amp/2`) and SNI routing — the detector pipeline already exists
    (`protocol/netty/detectors/`: `AMMHDetector` for the magic handshake,
    `HAProxyDetector`); add a TLS ClientHello detector beside them. Anti-DDoS boxes and
    tunnels that white-list TLS stop eating us. Magic packets stay as a legacy fallback
    behind the negotiated protocol version.
  - **HTTPS GET fallback endpoint** on the same host (one port: ALPN `amp/2` = fast
    path; `http/1.1` = plain HTTPS file serving). Suddenly nginx/Cloudflare/CDN/browser
    all "just work", and N3 falls out of it nearly for free.
- **N2. `/automodpack doctor` — S/M.** Kills the #1 support thread class. Server-side
  self-test: dial own `addressToSend:portToSend` from the outside path (or instruct a
  helper endpoint), verify TLS/cert/fingerprint, check bind conflicts, print a green/red
  checklist with the *exact* config fix per failure (the advice Skidam types by hand
  weekly: "open new TCP port, set bindPort + portToSend…"). Client-side `doctor` mirrors
  it. Also run automatically on first "Host server error" and print the checklist.
- **N3. Static / external hosting — M** · #232 #293 #26-adjacent, git/GH-Pages ask.
  Define the static layout: `automodpack-content.json` + `objects/<sha1>` (CAS blobs).
  `/automodpack export static <dir>` produces it; any HTTPS static host (GitHub Pages,
  S3, nginx) serves it; client accepts `https://…` as host address (plain-GET path from
  N1). Enables: no live server needed for downloads, singleplayer packs, packwiz-like
  workflows, "modpack hosted in a git repo".
- **N4. Velocity first-class — M** *(with Coio)*.
  With N1's configuration-phase re-negotiation: modpack re-check on `/server` backend
  switch, fingerprint handling at the proxy, one shared pack across backends (same
  modpackId ⇒ no restart), and retire loginphaseproxy's fragile interception for 1.20.2+.

---

## 3. T2 — Should ship within the 5.0.x beta cycle

- **D1. Download pipeline hardening — M** (T5: 1KB corrupted files, restart loops).
  Verify hash *before* committing to CAS (never link unverified bytes), stage-then-atomic-
  rename, retry with alternate source on mismatch, quarantine + report corrupted-source
  files. Skidam already called the old logic "fundamentally flawed" — CAS made the fix
  tractable.
- **D2. Update-loop breaker — S.** Client detects "N restarts without converging"
  (persisted counter), stops, and shows a diagnostic screen (what file keeps mismatching
  and why: hash vs size vs deletion) instead of looping. Fixes the *experience* of #343
  and friends even before root causes.
- **C1. Side auto-detection — M** · #370 #369.
  Read env/side metadata (fabric.mod.json `environment`, neo/forge `side`/dist +
  modrinth env data via U2) to auto-classify client-only/server-only; feed
  `autoExcludeServerSideMods` and warn on obvious misplacements. Detect
  `fml.toml`-style dependency overrides (#369) and sync them.
- **C2. Player-local mods contract — M** (T4).
  A blessed `mods-local/` (client-side) folder that AM guarantees never to touch, loaded
  alongside the modpack; per-player disable list for client-only modpack mods (Vercte's
  "remove Essential for me" ask) — with groups, admins can pre-bless which mods are
  player-removable.
- **C3. Sync rules cleanup — S/M** · #313 #288 #483.
  `syncedFiles`/`allowEditsInFiles` get: force-sync-on-change exception inside editable
  globs (`sync!` prefix or `forceSyncFiles` list), server `/automodpack push <glob>` to
  force-update editable files once, exclude-from-host list (#483), and a docs matrix of
  the interactions (X1). Consider `restartNotRequired` file list (#288) — with U4's
  staged updates this becomes "apply on the fly for these globs".
- **P1. Scan pruning — S.** Negated globs must prune directory traversal (never descend
  into `!/bluemap/**`); cap + log slow walks. Fixes the 70GB-folder 5-minute boot class.
- **P2. Compression strategy — S/M** · #497.
  Kill zstd-jni JPMS clashes for good: shade+relocate zstd-jni under our namespace, or
  switch to pure-Java zstd (aircompressor) since gzip fallback already exists. No more
  "reads more than one module zstd_jni", no more Android special cases.
- **P3. Transfer performance — M** · #457.
  Profile the 5-min-per-mod reports (likely per-file round-trips + tail latency):
  pipeline chunk requests, keep-alive reuse across files (single client exists on main),
  and range-parallelism for big files. Add a bandwidth self-test to N2's doctor.
- **L1. Loader sync expansion — M** · #290.
  `syncLoaderVersion` beyond Prism/MultiMC/Pandora where launcher APIs allow (ATLauncher?
  official launcher = likely impossible; document the matrix). Neo/forge-aware installer
  story: extend the existing installer to fetch the right neo/forge version (the
  "installer per loader-version" problem Skidam described).
- **L2. Server-remote client knobs — S.** Server config can pin client behaviors where
  it makes sense (e.g. force-disable `syncAutoModpackVersion` fleet-wide — func_kenobi's
  case; jvm-args stays out: #407 is a security no).
- **U5. In-game AutoModpack hub — M/L** (with Flagan11's designs).
  One screen from the pause/title menu: installed modpacks (M1 list, switch, delete),
  current pack contents (reuse U1 browser), group re-selection (G1), client config
  editor with validation, doctor button (N2), update check. Ends "edit JSON to switch
  packs".
- **X1. Docs overhaul — M** *(parallel)*.
  Rewrite quick-start around the real flow (incl. client-only mods and the new trust
  methods); certificate page → "four ways to pin"; sync-rules matrix; hosting-provider
  cookbook (the top-10 providers from Discord threads, each with known-good config);
  troubleshooting = doctor output explained.

---

## 4. T3 — Stretch / likely 5.1+

- **X2. iroh-based P2P host (bundled) — L.** Skidam's explicit plan (Oct 2025): endpoint
  id = ed25519 pubkey, E2E-encrypted even via relay, no port forwarding, no external
  program. Replaces zerotier/playit/hamachi use cases entirely; needs a lightweight
  relay for coordination (see §6 monetization). Rust/JNI or a Java QUIC impl decision up
  front; keep behind `hostBackend: p2p` config.
- **X3. Server→server sync — M** · #349. `automodpack pull <address>` on a dedicated
  server clones a host's pack (staging/mirror/dev-env use case).
- **X4. Client→server uploads (map folders) — M** · #433. Opt-in reverse channel with
  per-path quotas + validation; big feature, real demand (JourneyMap/Xaero sharing).
- **X5. Binary patch files — M** · #262. CAS makes chunk/rolling-hash diffs feasible;
  only worth it for big frequently-changed files (worlds/resource packs).
- **X6. Bukkit/paper plugin host — M** · #75. The velocity plugin proved the pattern;
  a Paper plugin serving the host + nag for client mods (no mod loading server-side).
- **X7. AutoPlug/packwiz interop — S each** · #248 #26. N3's static format is the
  interop surface; document mapping, accept packwiz-generated CAS trees.
- **X8. Waiting-music config — S** · #153. Point at an .ogg in the modpack. Cheap joy;
  rickrolls are a user-retention feature.

**Explicit non-goals for 5.0:** Quilt (#168) unless it's free via fabric path; MC ≤1.16
(#446); remote JVM args (#407, security); mod-side *enforcement* (advisory only).

---

## 5. Suggested release plan

| Milestone | Contents | Bar |
|---|---|---|
| **5.0.0-alpha** (internal/testers) | F1–F4, S5 stage 1, S1, S3, P1, P2 | autotester matrix green |
| **5.0.0-beta1** | G1 (groups+UI), U1+U2, M1+M2, S4, N2 | testers channel + real servers (Suerion, Mr.White, print(str(name)) volunteered infra before) |
| **beta2..n** | N1 progressive (ALPN first, config-phase next), U3, U4, M3, S2, S5 stage 2, D1, D2, C1–C3 | zero regressions on 4.x-era autotester scenarios; velocity scenario green |
| **5.0.0 stable** | L1, L2, U5, X1 docs, N3, N4 | 2 quiet beta weeks; support-thread rate visibly down |

Betas stay remote-downgrade-proof (as 4.0.0 did). Protocol negotiation keeps 4.0.5
clients connectable to 5.0 servers with old semantics wherever possible; where not,
the version-mismatch path already exists.

---

## 6. Monetization ideas (mod stays 100% free & open source)

1. **Relay/coordination service for X2 (P2P)** — the natural one: free tier
   (community relays, limited bandwidth) + paid relay bandwidth/regions for bigger
   communities. Costs scale with value delivered; nothing pay-walled in the protocol
   (self-host your own relay always possible — like iroh's own model).
2. **AutoModpack Cloud (hosted static host, N3)** — one command publishes your pack to
   a CDN-backed host with a stable URL + cert handled + download analytics. Free small
   tier; paid for size/traffic. Competes with "I can't open a port" (the #1 pain) —
   people already pay server hosts; this is cheaper than a second port upsell.
3. **Hosting-provider partnerships** — the doctor (N2) + cookbook (X1) name providers;
   affiliate/partner links for the ones with known-good one-click AM configs
   (exaroton-style). Zero user cost, aligns incentives.
4. **GitHub Sponsors / Ko-fi surfacing** — the ko-fi link exists but is buried in
   changelogs; put a subtle "supported by players like you" line on the (server-owner
   facing!) generation summary and doctor output, never on player screens.
5. **Priority support / SLA for networks** — big Velocity networks (800-player waiting
   lists showed up in threads) will pay for setup help; keep it as consulting, not
   features.

---

## 7. Cross-reference: request → roadmap item

| Source | Item |
|---|---|
| #472 PR groups, #416 #223 #151 #409 #309 | G1 |
| #459 PR | F1 |
| #488 fingerprint not saved, #447, #445 | S1–S6 |
| secrets expiry (Discord T3) | S5 |
| #402 #160 #170 | U1, U2 |
| #227 platform-only | U3 |
| #462 dup modpacks, #480, #326 | M1, M2 |
| pre-launch selector (Discord) | M3 |
| #294 #307 #398 #500 #493 #494 proxies | N1, N2, N4 |
| #232 #293 external/web host, #26 packwiz | N3, X7 |
| #370 #369 side detection | C1 |
| #501 #502 #477 player mods / deletions | C2, D1 |
| #313 #288 #483 sync rules | C3 |
| #457 slow downloads, #451 RAM | P3, P1 |
| #497 zstd modules, DH/OPAC clash | P2 |
| #290 loader update | L1 |
| #343 PCL2 loop, restart loops (T5) | D2, D1 |
| #433 map sync | X4 |
| #349 server2server | X3 |
| #262 patch files | X5 |
| #75 plugin | X6 |
| #248 AutoPlug | X7 |
| #153 music | X8 |
| #407 jvm args | rejected (security) |
| #446 old MC, #168 quilt | non-goals |
