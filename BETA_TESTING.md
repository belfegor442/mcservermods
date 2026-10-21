# Beta 0.2.0-beta.5 — Test checklist

## Install

1. Back up the world and the server's `config/gambling` directory.
2. Install Fabric Loader and Fabric API on the server with Java 25 or newer. Clients need Minecraft Java 26.2 but no mod at all; installing Fabric + the mod JAR on the client is optional and only changes the HUD (panel instead of sidebar).
3. Put the mod JAR in the server's `mods` folder only.
4. Start the server and confirm there are no mod initialization errors.
5. Check that `config/gambling/gambling.json` was created and the defaults look right; restart after editing it.
6. Join with a vanilla client and run `/chips`: the reply must show finished text (English/Spanish per client language), never raw `key.gambling.*` strings, and the sidebar HUD must appear on the right with chips, level, and state.
7. Join a second time with a client that has the mod installed (if available) and confirm the sidebar is NOT shown for that player (the panel HUD replaces it).

## Gameplay smoke tests

- The server log must show `gambling 0.2.0-beta.5` at boot (only this one gambling JAR may be present in `mods` — older versions side by side are ignored or break startup).
- On a vanilla client, joining shows the sidebar on the right: localized title (`CASINO` in Spanish), CHIPS/level/state lines, and no number column on the right edge.
- `/casino help` lists all three games. Plain `/casino` opens the graphical menu for players with the client mod (vanilla clients get the command list instead) and every resolved round shows a win/loss title with sound feedback.
- In the menu, an empty, non-numeric, over-maximum, or over-balance bet shows a red inline error and disables PLAY ROUND; valid input re-enables it.
- `/casino coinflip 10 heads` runs one round, deducts 10 CHIPS, and returns 20 CHIPS on a win or 0 on a loss.
- `/casino roulette 10 red` and the `black`/`green` choices produce a number from 0–36 and a matching color.
- Red/black roulette wins return 20 CHIPS for a 10-CHIP stake; green wins on zero return 360 CHIPS.
- `/casino slots 10` displays three symbols from a seven-symbol reel set. A pair returns 20 CHIPS, a non-SEVEN triple returns 80 CHIPS, and three SEVEN symbols return 200 CHIPS.
- After a round, the HUD keeps showing the game, bet, and result until the next round starts or the player disconnects. On a vanilla client this text lives in the right scoreboard sidebar; the sidebar updates after every round and disappears on disconnect.
- Running two casino rounds back-to-back faster than `roundCooldownMillis` (default 1 s) shows the cooldown message; after waiting, the round starts.
- `/gambling help` and plain `/gambling` list the commands; `/gambling sync` answers with a confirmation message after resending profile/HUD state.
- Plain `/casino` and `/casino help` list all three games.
- Bets above the current balance fail without making the balance negative.
- A second game cannot start while a session is active.
- Disconnecting with a committed but unresolved bet refunds the stake.
- After reconnecting, the balance and level remain synchronized.
- Admin commands (`/chips balance|set|add|remove|profile <player>`) also work with the name of an offline player who has joined before.
- Force a win with `/gambling resolve win` while the balance is at the cap: the stake is refunded and the session ends instead of getting stuck.
- Restart the server and verify the balance persists.
- Kill the server (not a clean stop) right after `/gambling bet` (op demo flow) and verify that restarting refunds the stake — and that killing it again right after that restart does NOT refund a second time.
- Corrupt `config/gambling/sessions.json` (make it invalid JSON) while a session file has a `.bak`: restarting loads the backup and refunds committed bets; the corrupt file is quarantined as `sessions.json.corrupt-*`.
- Test both English and Spanish game messages.

## Known beta limitations

- Players with the client mod get a graphical casino menu from plain `/casino`; vanilla clients get the command list and play with the `/casino <game>` commands. Both paths are equivalent.
- The panel HUD with the player portrait and the graphical menu require the mod on the client — a vanilla client only ever sees the scoreboard sidebar, chat messages, round titles, and sounds (the server cannot draw anything else).
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
