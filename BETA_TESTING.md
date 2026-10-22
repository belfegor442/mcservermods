# Beta 0.2.0-beta.9 — Test checklist

## Install

1. Back up the world and the server's `config/gambling` directory.
2. Install Fabric Loader and Fabric API on the server with Java 25 or newer. Clients need Minecraft Java 26.2 but no mod at all; installing Fabric + the mod JAR on the client is optional and only changes the HUD (panel instead of sidebar).
3. Put the mod JAR in the server's `mods` folder only.
4. For real block textures on vanilla clients, download `gambling-resources-0.2.0-beta.9.zip` from the release and set this line in `server.properties`, then restart:
   `resource-pack=https://github.com/belfegor442/mcservermods/releases/download/v0.2.0-beta.9/gambling-resources-0.2.0-beta.9.zip`
   Vanilla clients show a resource-pack prompt; accepting it replaces the purple missing-texture table with the gold casino table and the raw `block.gambling.casino_table` name with "Casino Table" / "Mesa de Casino".
5. Start the server and confirm there are no mod initialization errors.
5. Check that `config/gambling/gambling.json` was created and the defaults look right; restart after editing it.
6. Join with a vanilla client and run `/chips`: the reply must show finished text (English/Spanish per client language), never raw `key.gambling.*` strings, and the sidebar HUD must appear on the right with chips, level, and state.
7. Join a second time with a client that has the mod installed (if available) and confirm the sidebar is NOT shown for that player (the panel HUD replaces it).

## Gameplay smoke tests

- The server log must show `gambling 0.2.0-beta.9` at boot (only this one gambling JAR may be present in `mods` — older versions side by side are ignored or break startup).
- On a vanilla client, joining shows the sidebar on the right: localized title (`CASINO` in Spanish), grouped rows with blank spacer lines, a compact CHIPS balance (`1.5K` style), level and a white state line, and no number column on the right edge.
- Joining with a vanilla client automatically opens the casino chest menu: title `CASINO`, one-click game/option/bet selection, an emerald JUGAR RONDA block, a result anvil, a help book, and a barrier to close. No command is used at any point.
- Clicking any menu item plays a click sound and updates the menu instantly; clicking empty areas, dragging items, or pressing hotbar swap keys must not move or duplicate any menu item (the menu resyncs instead).
- The menu works identically with the client mod installed (same chest GUI).
- Creative tab → Functional Blocks contains the **Mesa de Casino / Casino Table** block (gold-trimmed table with a 7 on the sides and a diamond on top): place it and right-click it to open the casino menu; breaking it drops the block again. Without the resource pack step installed, vanilla clients see the purple missing-texture cube and the raw block name — that is a client-assets limitation, not a menu bug.
- A hand-edited `profiles.json` balance above the cap (`1,000,000,000,000`) loads clamped to the cap and logs a `clamped to the ... cap` warning at boot.
- `/casino help` lists the typed shortcuts. Plain `/casino` opens the clickable chest menu for every client (vanilla included), and every resolved round shows a win/loss title with sound feedback plus the result item in the menu.
- In the menu, picking a game auto-selects its first option; PLAY with an option-less state or an unaffordable bet shows the reason in the result anvil (red lore) instead of running the round.
- `/casino coinflip 10 heads` runs one round, deducts 10 CHIPS, and returns 20 CHIPS on a win or 0 on a loss.
- `/casino roulette 10 red` and the `black`/`green` choices produce a number from 0–36 and a matching color.
- Red/black roulette wins return 20 CHIPS for a 10-CHIP stake; green wins on zero return 360 CHIPS.
- `/casino slots 10` displays three symbols from a seven-symbol reel set. A pair returns 20 CHIPS, a non-SEVEN triple returns 80 CHIPS, and three SEVEN symbols return 200 CHIPS.
- After a round, the HUD keeps showing the game, bet, and result until the next round starts or the player disconnects. On a vanilla client this text lives in the right scoreboard sidebar; the sidebar updates after every round and disappears on disconnect.
- Running two casino rounds back-to-back faster than `roundCooldownMillis` (default 1 s) shows the cooldown message; after waiting, the round starts.
- `/gambling help` and plain `/gambling` list the commands; `/gambling sync` answers with a confirmation message after resending profile/HUD state.
- Plain `/casino` reopens the chest menu with the previous game, option, and bet selection preserved.
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

## Automated end-to-end check

- `gradlew runGametest` (needs a display and GPU) boots a dedicated test server, joins it with a real client, and verifies: menu auto-open on join with a translated title, closing, placing the casino table, right-clicking it to reopen the menu, clicking the JUGAR slot for a full round, and the balance changing. It fails the build on any raw `key.gambling.*` string or missing menu.

## Known beta limitations

- The clickable chest menu is fully server-driven and works for vanilla clients (it rides the vanilla container protocol); only the optional panel HUD requires the mod on the client. A vanilla client sees the chest menu, the scoreboard sidebar, chat messages, round titles, and sounds.
- The manual `/gambling start`, `bet`, `resolve`, and `finish` flow is an operator-only demo tool; regular players use the chest menu or the `/casino <game>` shortcuts.
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
