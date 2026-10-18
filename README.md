# mcservermods

Fabric mod for Minecraft Java Edition 1.21.1, built with Java 21.

## Current beta scope

- Server-authoritative virtual CHIPS balances and player profiles.
- Persistent JSON profiles, transaction log, periodic saves, and balance/profile synchronization.
- Playable coin-flip and European roulette games, with server-side randomness and game-specific payouts.
- A lightweight HUD showing the player's balance, level, and active round state.
- English and Spanish messages.
- Regression tests for economy, session transitions, disconnect refunds, failed saves, and payout-cap handling.

CHIPS are fictional in-game points. They have no real-money value and cannot be converted to money.

## Requirements

- Minecraft Java Edition 1.21.1
- Fabric Loader and Fabric API compatible with 1.21.1
- Java 21

## Player commands

- `/chips` — show your CHIPS balance and level.
- `/casino help` — show available casino commands.
- `/casino coinflip <bet> <heads|tails>` — wager CHIPS on a server-randomized coin flip. A win returns 2× the stake; a loss forfeits the stake.
- `/casino roulette <bet> <red|black|green>` — single-zero European roulette. Red/black wins return 2× the stake; a green/zero win returns 36× the stake.
- `/gambling sync` — request a profile/HUD resynchronization.
- `/gambling session` — inspect the current session, if any.

Administrative economy commands require permission level 2: `/chips set`, `/chips add`, `/chips remove`, and `/chips balance <player>`.

## Build and test

Run from the repository root with Java 21 installed:

```sh
./gradlew --no-daemon clean test
./gradlew --no-daemon build
```

On Windows, use `gradlew.bat` instead. GitHub Actions runs the same verification on pushes to `main` and `fix/**`, pull requests targeting `main`, and manual dispatches.

## Beta limitations

This is an early beta, not a finished multi-game casino or War mod. Coin flip and European roulette are the first playable games. Blackjack, slots, a graphical casino menu, and the War mode are not implemented yet. Back up the server's `config/gambling` directory before testing updates.
