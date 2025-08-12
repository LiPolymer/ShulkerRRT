# ShulkerRRT
[中文](./README_zh.md)

A lightweight automatic resource pack reloading tool for Minecraft.

## Features

- **Resource Pack Folder Monitoring**:
  - Automatically monitors changes in the vanilla resource pack folder and reloads
  - Supports monitoring changes in [Paxi](https://modrinth.com/mod/paxi) resource pack folders
- **Focus Regained Reloading**:
  - Optional automatic resource pack reloading when game window focus is regained
- **Configurability**:
  - All features can be individually enabled/disabled via configuration file

## Installation Requirements
- Minecraft 1.21.x (for this branch) / 1.20.1
- Fabric Loader or NeoForge + [Sinytra Connector](https://modrinth.com/mod/connector)

#### Licensed under [GPLv3](https://www.gnu.org/licenses/quick-guide-gplv3.zh-cn.html)

## Configuration
Configuration file located at `config/shulkerRRT.json`:

```json5
{
    "isShulkerRDKManaged": false,  // Managed by ShulkerRDK (this tool not yet fully developed)
    "monitorDefaultPath": true,    // Monitor vanilla resource pack folder
    "monitorPaxiPath": true,       // Monitor Paxi resource pack folders
    "reloadOnFocusGained": false   // Reload on focus regained
}
```