# mcservermods

Fabric mod for Minecraft Java Edition 1.21.1, built with Java 21.

## Current beta scope

- Server-authoritative virtual CHIPS balances and player profiles.
- Persistent JSON profiles, transaction log, periodic saves, and balance/profile synchronization.
- Crash recovery: active sessions are persisted and any unresolved bet is refunded automatically on the next startup.
- Economy safety: balances loaded from disk are clamped to the cap, and a win that would exceed the cap refunds the stake instead of locking the session.
- The transaction log rotates at 10 MB so it cannot grow without bound.
- Playable coin-flip, European roulette, and three-reel slots games, with server-side randomness and game-specific payouts.
- A lightweight HUD showing the player's balance, level, and active round state.
- English and Spanish messages.
- Regression tests for economy, session transitions, disconnect refunds, failed saves, payout-cap handling, slots/roulette odds, and session crash recovery.

CHIPS are fictional in-game points. They have no real-money value and cannot be converted to money.

## Requirements

- Minecraft Java Edition 1.21.1
- Fabric Loader and Fabric API compatible with 1.21.1
- Java 21

## Player commands

- `/chips` — show your CHIPS balance and level.
- `/chips profile` — show your full profile, including the active session (if any).
- `/casino help` (or plain `/casino`) — show available casino commands.
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

Run from the repository root with Java 21 installed:

```sh
./gradlew --no-daemon clean test
./gradlew --no-daemon build
```

On Windows, use `gradlew.bat` instead. GitHub Actions runs the same verification on pushes to `main` and `fix/**`, pull requests targeting `main`, and manual dispatches.

## Beta limitations

This is an early beta, not a finished multi-game casino or War mod. Coin flip, European roulette, and slots are playable. Blackjack, a graphical casino menu, and the War mode are not implemented yet. Back up the server's `config/gambling` directory before testing updates.
