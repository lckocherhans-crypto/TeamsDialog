# TeamsDialog (Paper 26.2)

A dialog-driven teams plugin. Everything happens in native Minecraft dialog screens.

## Build
Requires JDK 25.

    gradle wrapper      # once, if you don't have gradlew yet
    ./gradlew build

The jar lands in `build/libs/TeamsDialog-1.0.0.jar` -> drop it in your server's `plugins/` folder.

## Commands
- `/team` – open the menu (alias `/teams`)
- `/team invites | top | home | chat <msg>`
- `/tc <msg>` – team chat; `/tc` alone toggles team-chat mode

## Features
- Create teams via a form (name, tag, colour picker, friendly-fire toggle)
- Coloured names + [TAG] prefix in tab list, nametags and chat
- Roles: Owner / Officer / Member (promote, demote, kick, transfer ownership)
- Click-to-pick invite list (only shows teamless online players) + clickable chat invites with expiry
- Members page with live "radar": distance + direction arrow to each teammate
- Team home with a stand-still warmup (actionbar countdown + particles)
- Private team chat (toggle mode or /tc)
- Friendly-fire protection (melee + projectiles), toggled per team
- Kill leaderboard ("Top Teams")
- Join/leave notifications for teammates, YAML persistence (teams.yml)

## Permissions
- `teams.use` (default: true)
- `teams.bypass.warmup` (default: op)
