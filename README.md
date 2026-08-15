# GTNH QoL Improvements

[简体中文](README_ZH_CN.md)

A lightweight quality-of-life mod for GT New Horizons 1.7.10 Daily, focused on improving Vajra interactions, AE2 pattern encoding, and quest detection. Every feature can be toggled independently in the configuration GUI.

![Quick Encoding Terminal](docs/images/quick-encoding-terminal.png)

## Features

### Quick Encoding Terminal

- Combines ME storage, the Interface Terminal, and pattern encoding in one screen.
- Provides an early-game panel version that attaches directly to an ME cable. It only requires a regular ME Pattern Terminal and ME Interface Terminal, and supports crafting and 3×3 processing patterns.
- Supports 3×3 crafting, 3×3 processing, and 4×4 processing patterns, with automatic recipe-type selection.
- Supports NEI recipe transfer, Alt-click encoding/search/upload, and search suffixes for GT virtual circuits and non-consumable ingredients.
- Middle-click NEI bookmarks to request autocrafting. Middle-click blocks in the world to select them from the player inventory, extract them from ME storage, or open an autocrafting request when none are available.
- Fits in the Baubles Expanded `Terminal` slot and can be opened with a configurable keybind.
- Can be crafted with an AE2FC Energy Card or Quantum Bridge Card for infinite power or unlimited range and cross-dimensional access.

The wireless version is crafted from an ME Wireless Terminal, ME Extended Pattern Terminal, and ME Interface Terminal, and provides full 4×4 processing support. The panel version needs neither wireless components nor the ME Extended Pattern Terminal, making it suitable for early AE progression.

### Vajra Improvements

- Replaces mined blocks with blocks from the offhand.
- Adds GT wrench and wire-cutter grid interactions for rotating machines and changing cable or pipe connections.
- Adds hold-to-mine protection with creative-mode-like handling. Quick clicks remain unaffected, reducing accidental machine and cable removal.

### ME Quest Detector

- Binds to the placing player or party, connects to an ME network, and consumes one channel.
- Uses network items and fluids for BetterQuesting detection tasks. The Detect/Submit button can also consume ME inventory for submission tasks while respecting AE permissions.
- Supports BetterQuesting ore-dictionary, NBT, and fuzzy matching rules. Native ME fluids and fluids stored in GT cells or other containers are both recognized.
- Uses targeted storage watchers, low-frequency fallback scans, and per-network/party deduplication to control overhead on large quest books and ME networks.

## Configuration

Configure the mod through `Mods -> GTNH QoL Improvements -> Config`. The configuration file is `config/gtnh_qol_improvements.cfg`.

Main switches:

- `vajraOffhandReplacement`
- `vajraToolFunctions`
- `dualTerminal`
- `terminalGtRecipeSearchSuffix`
- `middleClickOrdering` (enabled by default)
- `craftingTreeMissingBranches`
- `questDetector` (enabled by default)

## Requirements

Designed for GTNH Daily instances containing AE2, AE2 Fluid Crafting, GregTech 5U, BetterQuesting, NEI, Backhand, and Baubles Expanded. The mod must be installed on both the client and server.

The repository also includes a [Modernity Dark UI compatibility resource pack](resourcepack/modernity-dark-ui). Place it above Modernity in the resource-pack list. Every tagged release automatically includes the ZIP.

## Credits

The Quick Encoding Terminal was inspired by [AE2Things](https://github.com/asdflj/AE2Things). Thanks to its authors and contributors for their work for the GTNH community.

The ME Quest Detector references the AE quest-detection approaches used by [ME_Quests_Detector](https://github.com/illuciaz23/ME_Quests_Detector) and GTLSupb. Its block textures are reused under the original author's MIT license.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
