# GTNH QoL Improvements

面向 GT New Horizons 1.7.10 daily 的独立 QoL 模组。

## 功能

模组配置页只有三个独立开关：

1. **金刚杵副手方块替换**：使用 GraviSuite 或 GregTech 金刚杵挖掘时，下一 tick 用 Backhand 副手中的方块替换原方块，并走正常 Forge 放置规则。
2. **金刚杵扳手 / 剪线钳**：金刚杵右键 GT 机器、物品管道和流体管道时执行原生扳手行为；右键 GT 线缆时执行原生剪线钳连接行为。也支持 AE2 `IOrientable`、IC2 `IWrenchable` 和 Forge 可旋转方块。
3. **快速编码终端 / Alt+NEI 编码 / 搜索与转移**：添加 `ME无线快速编码终端`。它能放入 Baubles Expanded 的 `Terminal` 饰品槽，并在同一界面中提供 ME 库存、接口终端和样板编码。终端支持 3×3 合成、3×3 处理与 4×4 处理样板；按住 Alt 点击 NEI 的 `+` 可自动识别配方、编码、搜索并上传样板。

快捷键默认未绑定，可在控制设置中的 `GTNH QoL Improvements` 分类绑定。

合成配方为无序合成：

- ME 无线终端
- ME 扩展样板终端
- ME 接口终端

输出的快速编码终端需要像普通无线终端一样充电并绑定安全终端。它可以继续与 AE2FC 的能量卡或量子桥卡无序合成，分别获得无限电力或无视距离与维度限制的能力。

## 配置

游戏内进入 `Mods -> GTNH QoL Improvements -> Config`。配置文件位于：

`config/gtnh_qol_improvements.cfg`

三个键分别是：

- `vajraOffhandReplacement`
- `vajraToolFunctions`
- `dualTerminal`

## 构建

项目使用 GTNH Gradle 模板，当前版本为 `1.2.0`：

```powershell
.\gradlew.bat updateBuildScript
.\gradlew.bat build
```

产物位于 `build/libs/`。

## 目标依赖

- Applied Energistics 2 `rv3-beta-1029-GTNH`
- AE2 Fluid Crafting `1.5.100-gtnh`
- GregTech 5U `5.09.54.79`
- NotEnoughItems `2.8.120-GTNH`
- Backhand `1.8.13`
- Baubles Expanded `2.2.22-GTNH`

代码针对 GTNH Daily 670 依赖集编译和验证。
