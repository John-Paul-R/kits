<div align="center">

<img alt="Kits Icon" src="src/main/resources/assets/kits/icon.png" width="128">

# Kits

Permissions-based player kits for Fabric Servers.

[![Release](https://img.shields.io/github/v/release/John-Paul-R/kits?style=for-the-badge&include_prereleases&sort=semver)][releases]
[![Available For](https://img.shields.io/badge/dynamic/json?label=Available%20For&style=for-the-badge&color=34aa2f&query=$[:]&url=https%3A%2F%2Fwww.jpcode.dev%2Fkits%2Fsupported_mc_versions.json)][modrinth:files]


[![Modrinth Downloads](https://img.shields.io/modrinth/dt/kits?color=00AF5C&label=modrinth&style=for-the-badge&logo=modrinth)][modrinth:files]
[![Curseforge Downloads](https://img.shields.io/badge/dynamic/json?color=f16436&style=for-the-badge&label=CurseForge&query=downloadCount&url=https://www.fibermc.com/api/v1.0/ForeignMods/507127&logo=CurseForge)][curseforge:files]
[![GitHub Downloads (all releases)](https://img.shields.io/github/downloads/John-Paul-R/kits/total?style=for-the-badge&amp;label=GitHub&amp;prefix=downloads%20&amp;color=4078c0&amp;logo=github)][releases]

</div>

Kits is a **Minecraft mod** for [Fabric][fabric] servers that adds configurable,
permissions-based player kits. This allows server owners to easily set up
batches of items that their players can claim, depending on their rank.
Additionally, kits can have a predefined cooldown, so that players do not get to
use them infinitely.

**Compatible with LuckPerms**

## Usage

### Adding Kits

Command: `/kit add <kit_name> <cooldown_milliseconds>`\
Requires Permission: `kits.manage`

To add a kit, simply place the items you would like to be included in this kit
anywhere in your inventory and run the command `/kit add`.

### Claiming Kits

Command: `/kit claim <kit_name>`\
Requires Permission: `kits.claim.<kit_name>`

For players, claiming kits is simple. They simply type `/kit claim <kit_name>`.
If they have permissions to use the specified kit, and that kit is not on
cooldown for them, they receive the kit in their inventory (or on the ground if
their inventory is full.)

Brigadier suggestions are enabled, and will only suggest kits the player has
permissions for.

### Removing Kits

Command: `/kit remove <kit_name>`\
Requires Permission: `kits.manage`

To remove a kit, simply type `/kit remove <kit_name>`. This *irreversibly and
completely* **deletes** the specified kit from the server, for all users.

### Reset Player Kit Cooldowns

If, as a moderator, you wish to reset a player's kit cooldowns, allowing them to
regain access to already-claimed kits, you can do so with the following commands:

#### Reset a single kit

Command: `/kit resetPlayerKit <player> <kit_name>`\
Requires Permission: `kits.manage`

#### Reset all kits

Command: `/kit resetPlayer <player>`\
Requires Permission: `kits.manage`

## Advanced

**Kits** stores most of its data in `nbt` files, using Minecraft's NBT format.
You can modify and create such files with a tool like [NBTExplorer][nbtexp].

### Kit Config Files

If, for whatever reason, you wish to configure kits manually, instead of using
the built-in commands, you can do so by modifying the kit files directly.

Kit files are stored in the directory `config/kits` in Minecraft's NBT format.

This allows you to modify kit contents or cooldowns after creation, or even add
entirely new kits.

### Player Kit Usage Files

Whenever a player successfully claims a kit, the time at which this kit was
claimed is recorded in a user-specific file named `<player_uuid>.nbt` in the directory
`world/kits_user_data`.

If you wish to reset or modify when a user can next use a kit that they have
already claimed, you can edit the time specified in this file (stored as
[milliseconds since Epoch](https://www.epochconverter.com/)). To reset all kit
cooldowns for a given user, simply delete the nbt file that corresponds to them.

---

## Contributing

Thank you for considering contributing to Kits! Please see the
[Contribution Guidelines][contributing].

## Licence

Kits is open-sourced software licenced under the [MIT license][licence].

## Discord

Questions? Contact me in [my Discord server][discord].

[nbtexp]: https://github.com/jaquadro/NBTExplorer
[contributing]: .github/CONTRIBUTING.md
[curseforge]: https://curseforge.com/minecraft/mc-mods/kits
[curseforge:files]: https://www.curseforge.com/minecraft/mc-mods/kits/files/all
[modrinth:files]: https://modrinth.com/mod/kits/versions
[fabric]: https://fabricmc.net/
[licence]: LICENCE
[minecraft]: https://minecraft.net/
[releases]: https://github.com/John-Paul-R/kits/releases
[security]: .github/SECURITY.md
[discord]: https://discord.jpcode.dev/
