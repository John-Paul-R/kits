
# Kits

Permissions-based player kits for Fabric Servers.

[![Release](https://img.shields.io/github/v/release/John-Paul-R/kits?style=for-the-badge&include_prereleases&sort=semver)][modrinth:files]

Kits is a **Minecraft mod** for [Fabric][fabric] servers that adds configurable,
permissions-based player kits. This allows server owners to easily set up
batches of items that their players can claim, depending on their rank.
Additionally, kits can have a predefined cooldown, so that players do not get to
use them infinitely.

**Compatible with LuckPerms**

## Usage

### Adding Kits

Command: `/kit add [kit_name] [cooldown_milliseconds]`

Requires Permission: `kits.manage`

To add a kit, simply place the items you would like to be included in this kit
anywhere in your inventory and run the command `/kit add`.

### Claiming Kits

Command: `/kit claim [kit_name]`

Requires Permission: `kits.claim.[kit_name]`

For players, claiming kits is simple. They simply type `/kit claim [kit_name]`.
If they have permissions to use the specified kit, and that kit is not on
cooldown for them, they receive the kit in their inventory (or on the ground if
their inventory is full.)

Brigadier suggestions are enabled, and will only suggest kits the player has
permissions for.

### Removing Kits

Command: `/kit remove [kit_name]`

Requires Permission: `kits.manage`

To remove a kit, simply type `/kit remove [kit_name]`. This *irreversibly and
completely* **deletes** the specified kit from the server, for all users.

## Advanced

### Files

If, for whatever reason, you wish to configure kits manually, instead of using
the built-in commands, you can do so by modifying the kit files directly.

Kit files are stored in the directory `config/kits` in Minecraft's NBT format.
You can modify and create such files with a tool like [NBTExplorer](nbtexp).

This allows you to modify kit contents or cooldowns after creation, or even add
entirely new kits.

---

## Contributing

Thank you for considering contributing to Kits! You can do so on the project's
[GitHub page][github].

## Licence

Kits is open-sourced software licenced under the [MIT license][licence].

## Discord

Questions? Contact me in [my Discord server][discord].

[nbtexp]: https://github.com/jaquadro/NBTExplorer
[github]: https://github.com/John-Paul-R/kits
[curseforge]: https://curseforge.com/minecraft/mc-mods/kits
[curseforge:files]: https://www.curseforge.com/minecraft/mc-mods/kits/files/all
[modrinth:files]: https://modrinth.com/mod/kits/versions
[fabric]: https://fabricmc.net/
[licence]: https://cdn.modrinth.com/licenses/mit.txt
[minecraft]: https://minecraft.net/
[releases]: https://github.com/John-Paul-R/kits/releases
[security]: .github/SECURITY.md
[discord]: https://discord.jpcode.dev/
