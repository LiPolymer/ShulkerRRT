# ShulkerRRT
[English](./README.md)

一个轻量级的 Minecraft 资源包自动重载工具

## 功能特性

- **资源包文件夹监测**：
  - 自动监测原版资源包文件夹变动并重载
  - 支持 [Paxi](https://modrinth.com/mod/paxi) 资源包文件夹变动监测
- **焦点回归重载**：
  - 可选游戏窗口焦点回归时自动重载资源包
- **可配置性**：
  - 所有功能均可通过配置文件单独启用/禁用

## 安装要求
- Minecraft 1.21.1
- Fabric Loader 或 NeoForge + [Sinytra Connector](https://modrinth.com/mod/connector)

#### 本项目采用 [GPLv3](https://www.gnu.org/licenses/quick-guide-gplv3.zh-cn.html) 获得许可

## 配置
配置文件位于 `config/shulkerRRT.json`：

```json5
{
    "isShulkerRDKManaged": false,  // 由ShulkerRDK管理（该工具暂未开发完成）
    "monitorDefaultPath": true,    // 监测原版资源包文件夹
    "monitorPaxiPath": true,       // 监测Paxi资源包文件夹
    "reloadOnFocusGained": false   // 焦点回归时重载
}
```