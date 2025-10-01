# Kit Rings

Kit Rings are collections of kits that share a common cooldown. When a player
claims any kit from a ring, all kits in that ring go on cooldown together. This
is useful for creating tiered kit systems or grouped kits where players should
only be able to choose one option within a time period.

## Usage

### Adding Kit Rings

Command: `/kit ring add <ring_name> <cooldown> [time_unit]`\
Requires Permission: `kits.manage`

To add a kit ring, run the command with your desired ring na me and cooldown
settings.

> [!NOTE]
> `[time_unit]` may be one of `d` (day), `h` (hour), `m` (minute), `s` (second),
`y` (year). If no time unit is specified, `cooldown` is interpreted as
> milliseconds.

### Adding Kits to Rings

Command: `/kit ring addKit <ring_name> <kit_name>`\
Requires Permission: `kits.manage`

To add an existing kit to a ring, use this command. The kit will inherit the
ring's cooldown behavior.

### Removing Kits from Rings

Command: `/kit ring removeKit <ring_name> <kit_name>`\
Requires Permission: `kits.manage`

This removes a kit from the ring and converts it back to a standalone kit.

### Setting Display Item

Command: `/kit ring setDisplayItem <ring_name> <item>`\
Requires Permission: `kits.manage`

Sets the display item shown in the kit selection GUI for this ring.

### Removing Rings

#### Remove Ring (Extract Kits)

Command: `/kit ring removeRing <ring_name>`\
Requires Permission: `kits.manage`

Removes the ring and converts all its kits to standalone kits.

#### Remove Ring and All Kits

Command: `/kit ring removeRingAndContainedKits <ring_name>`\
Requires Permission: `kits.manage`

This _irreversibly and completely_ **deletes** the specified ring and all kits
it contains.

### Reset Player Ring Selection

Command: `/kit resetPlayerRingSelection <player> <ring_name>`\
Requires Permission: `kits.manage`

Resets the cooldown for a specific ring, allowing the player to claim from it
again.

### Managing Ring Commands

Just like individual kits, rings can execute commands when claimed:

- `/kit ring commands <ring_name> list` - List all commands for a ring
- `/kit ring commands <ring_name> add <command>` - Add a command to execute when
  any kit from this ring is claimed
- `/kit ring commands <ring_name> remove <command>` - Remove a command from the
  ring

## Advanced

### Ring Directory Structure

Kit rings are stored in the `config/kits` directory using a directory-based
format:

```
config/kits/
├── ringname.ring/
│   ├── _ring.json          # Ring metadata (cooldown, display name, display item, commands)
│   ├── kitname1.json       # Individual kit file
│   └── kitname2.json       # Individual kit file
└── standalone_kit.json     # Regular standalone kit
```

This structure allows you to manage ring membership by simply moving kit files
in and out of ring directories.

### Ring Metadata

The `_ring.json` file contains only the ring's metadata:

- `cooldown` - The cooldown in milliseconds for all kits in this ring
- `display_name` (optional) - The display name shown in GUIs
- `display_item` (optional) - The item shown in the kit selection GUI
- `commands` - List of commands to execute when any kit from this ring is
  claimed

Individual kit files are stored separately as `<kitname>.json` files within the
ring directory.

### Legacy Format Migration

Kits automatically migrates old single-file ring formats (`.ring.json` and
`.ring.nbt`) to the new directory structure on first load.

### Permissions

Kits in a ring require the permission `kits.claim.<ring_name>` rather than
`kits.claim.<kit_name>`. This allows you to grant access to all kits in a ring
with a single permission.
