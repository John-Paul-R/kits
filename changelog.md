

## Kits `v1.7.1-mc1.21.7`

### Changes

- update to Minecraft 1.21.7
- Update kit serialization to match new `PlayerInventory` serialization strategy
- fix kits sometimes using a nested directory for storing kit files
  - you'll get a message in your server console on server start if you're
    affected with instructions on how to fix, if it can't be auto-fixed
- assorted build/tooling/dependency/publishing updgrades

--- --- ---

## Kits `v1.7.0-mc1.21.5`

### Changes

- fix enchanted items (#28) (by @arnokeesman)
- update for 1.21.2 (by @arnokeesman)
- add config option to change the kits menu title (#31) (by @arnokeesman)
- add messaging and logic for one time use kits (#32) (by @arnokeesman)
- fix storing enchanted armor and offhand as well

Big thanks to Arno for being an absolute powerhouse in this release.

