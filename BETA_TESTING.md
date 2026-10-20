# Beta 0.1.0-beta.1 — Test checklist

## Install

1. Back up the world and the server's `config/gambling` directory.
2. Install Minecraft Java 1.21.1, Fabric Loader, and Fabric API on the client and server.
3. Put the mod JAR in the server's `mods` folder. Install it on the client too for the custom HUD and translated client state.
4. Start the server and confirm there are no mod initialization errors.
5. Join and run `/chips` to verify that a profile is created and the starting balance is shown.

## Gameplay smoke tests

- `/casino help` lists all three games.
- `/casino coinflip 10 heads` runs one round, deducts 10 CHIPS, and returns 20 CHIPS on a win or 0 on a loss.
- `/casino roulette 10 red` and the `black`/`green` choices produce a number from 0–36 and a matching color.
- Red/black roulette wins return 20 CHIPS for a 10-CHIP stake; green wins on zero return 360 CHIPS.
- `/casino slots 10` displays three symbols. A pair returns 20 CHIPS, a non-SEVEN triple returns 80 CHIPS, and three SEVEN symbols return 200 CHIPS.
- After a round, the HUD keeps showing the game, bet, and result until the next round starts or the player disconnects.
- Plain `/casino` and `/casino help` list all three games.
- Bets above the current balance fail without making the balance negative.
- A second game cannot start while a session is active.
- Disconnecting with a committed but unresolved bet refunds the stake.
- After reconnecting, the balance and level remain synchronized.
- Restart the server and verify the balance persists.
- Test both English and Spanish game messages.

## Known beta limitations

- The games currently use commands and chat feedback; a graphical casino menu is not included.
- The manual `/gambling start`, `bet`, `resolve`, and `finish` flow is an operator-only demo tool; regular players use the `/casino` games.
- Blackjack, War, weapon systems, ammunition, reloads, explosives, teams, and map objectives are not implemented in this beta.
- The economy is server-authoritative, but this is still a beta. Keep backups and do not treat CHIPS as real-money value.

## Bug report template

- Minecraft version:
- Fabric Loader / Fabric API versions:
- Server logs / stack trace:
- Command and exact steps to reproduce:
- Expected result:
- Actual result:
- Did the balance change unexpectedly? If so, include the before/after values:
