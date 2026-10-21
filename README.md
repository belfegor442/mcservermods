# mcservermods

[![Gradle verification](https://github.com/belfegor442/mcservermods/actions/workflows/gradle.yml/badge.svg)](https://github.com/belfegor442/mcservermods/actions/workflows/gradle.yml)
[![Release](https://github.com/belfegor442/mcservermods/actions/workflows/release.yml/badge.svg)](https://github.com/belfegor442/mcservermods/actions/workflows/release.yml)

Fabric mod for Minecraft Java Edition 26.2, built with Java 25.

## Current beta scope

- Server-authoritative virtual CHIPS balances and player profiles.
- Persistent JSON profiles, transaction log, periodic saves, and balance/profile synchronization.
- Crash recovery: active sessions are persisted and any unresolved bet is refunded automatically on the next startup. Both `profiles.json` and `sessions.json` keep a `.bak` copy: a corrupt or lost primary file falls back to the backup, and unreadable files are quarantined as `*.corrupt-<timestamp>` instead of being overwritten.
- Economy safety: balances loaded from disk are clamped to the cap, and a win that would exceed the cap refunds the stake instead of locking the session.
- The transaction log rotates at 10 MB so it cannot grow without bound.
- Playable coin-flip, European roulette, and three-reel slots games, with server-side randomness and game-specific payouts.
- Works on the server alone: chat messages are translated server-side to the language reported by the client, and clients without the mod get a status sidebar (chips, level, state, active game) in the vanilla scoreboard HUD.
- Optional client mod: adds a custom panel HUD with the player portrait; players with the client mod skip the sidebar and see the panel instead.
- English and Spanish messages.
- Regression tests for economy, session transitions, disconnect refunds, failed saves, payout-cap handling, slots/roulette odds, and session crash recovery.

CHIPS are fictional in-game points. They have no real-money value and cannot be converted to money.

## Requirements

- **Server:** Minecraft Java Edition 26.2, Fabric Loader and Fabric API compatible with 26.2, Java 25.
- **Client (vanilla):** nothing to install. Minecraft 26.2 works out of the box; the server-side translation and scoreboard sidebar cover all gambling messages and status.
- **Client (optional):** install the same Fabric setup only if you want the custom panel HUD.

## Player commands

- `/chips` — show your CHIPS balance and level.
- `/chips profile` — show your full profile, including the active session (if any).
- `/casino help` (or plain `/casino`) — show available casino commands. Plain `/casino` opens the interactive casino menu for players with the client mod; vanilla clients get the command list instead.
- `/casino coinflip <bet> <heads|tails>` — wager CHIPS on a server-randomized coin flip. A win returns 2× the stake; a loss forfeits the stake.
- `/casino roulette <bet> <red|black|green>` — single-zero European roulette. Red/black wins return 2× the stake; a green/zero win returns 36× the stake.
- `/casino slots <bet>` — three-reel slots with seven symbols. Any pair returns 2×, a triple returns 8×, and three SEVEN symbols return 20× the stake.
- `/gambling cancel` — abandon a session before committing the bet.
- `/gambling sync` — request a profile/HUD resynchronization.
- `/gambling help` — list the gambling commands.

After a casino round resolves, the result stays visible on the HUD until the next round starts or the player disconnects.

## Configuration

`config/gambling/gambling.json` is created on first start (invalid values fall back to defaults; restart the server after editing):

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `defaultChips` | `1000` | 0 – 10^12 | CHIPS granted to new players |
| `maxBet` | `1000000000` | 1 – 10^9 | Highest bet accepted by `/casino` and `/gambling` |
| `autosaveSeconds` | `60` | 10 – 36000 | Economy autosave interval |
| `roundCooldownMillis` | `1000` | 0 – 60000 | Minimum delay between casino rounds (0 disables) |

Administrative commands require permission level 2: `/chips set`, `/chips add`, `/chips remove`, `/chips balance <player>`, `/chips profile <player>`, `/gambling start`, `/gambling bet`, `/gambling resolve`, and `/gambling finish`. Admin commands target online players first and fall back to the stored profile of an offline player by name.

## Build and test

Run from the repository root with Java 25 installed:

```sh
./gradlew --no-daemon clean test
./gradlew --no-daemon build
```

On Windows, use `gradlew.bat` instead. GitHub Actions runs the same verification on pushes to `main` and `fix/**`, pull requests targeting `main`, and manual dispatches; the JARs and an HTML coverage report (JaCoCo) are uploaded as run artifacts.

Pushing a tag starting with `v` (for example `v0.1.0-beta.7`) triggers the release workflow, which builds the mod and publishes a GitHub release containing the mod JARs.

## Beta limitations

This is an early beta, not a finished multi-game casino or War mod. Coin flip, European roulette, and slots are playable. Blackjack, a graphical casino menu, and the War mode are not implemented yet. Back up the server's `config/gambling` directory before testing updates.
