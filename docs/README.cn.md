<p align="center"><img src="icon.png" width="128" alt="icon"></p>
<h1 align="center">QoL Bundle</h1>
<p align="center">39 个纯客户端的 QoL 小功能装进一个模组，每个都有自己的开关和设置。</p>

<div align="center">
<p align="center">-><a href="../README.md">English</a><-</p>
<p align="center">-><a href="README_detailed.md">详细手册</a><-</p>

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue)

</div>

## 一些功能

| 功能 |详情|
|:---|:---|
|**声音方向罗盘**|显示声源的方向|
|**投掷物落点**|手持投掷物时，显示落地区域是否安全。|
| **落地伤害预告** | 显示即将受到的掉落伤害，并在致命时发出警报。 |
| **岩浆安全网** | 警告附近的岩浆并引导你前往安全区域。 |
| **逃跑轨迹** | 视觉化你的路径，帮助你在受伤时撤退。 |
| **传送门计算器** | 预览跨维度坐标并链接可见的传送门。 |
| **鞘翅飞行仪表盘** | 显示飞行遥测数据、烟花火箭数量和预测的着陆点。 |
| **放置大师** | 提供方块预览与方块状态，并可锁定放置方向。 |
| **箱子记忆** | 追踪容器内的物品并引导你找到它们。 |
| **合成书增强** | 追踪缺失的原料并定位它们的存储位置。 |
| **对手装备情报** | 显示所注视玩家的装备、附魔和当前动作。 |
| **接近警报** | 当有玩家进入 12 格范围内时发出声音并指向该玩家。 |
| **被盯上检测** | 如果有玩家瞄准你超过 3 秒，则向你发出警告。 |
| **红石诊断台** | 扫描机械以映射信号、检测瓶颈、列出组件、追踪物品输出并查看单层切片。|

<br>

| 灰档（默认关闭） | 详情 |
| :--- | :--- |
| **挂机连点器 (F7)** | 自动点击/长按；受到伤害时停止。拥有预设配置。 |
| **自由视角 (F6)** | 带有标记的自由飞行摄像机；受到伤害时结束。 |
| **鞘翅起飞 (V)** | 一键完成跳跃、开启鞘翅并点燃火箭。 |
| **水与岩浆夜视** | 清除水下迷雾并提高岩浆中的能见度。 |
| **后视镜** | 显示实时后方视角，但会导致帧率减半。 |


### X-ray 附属包（单独下载）

X-ray 是特意没放进 QoL 全家桶的。很多服务器禁止透视。你可以单独下载透视附加包。和全家桶一起下载的话，透视设置会显示在设置面板上。

## 截图

![信息 HUD、重生点那一行和背包空格数](images/hud-overview.png)

左上角是信息 HUD（坐标、朝向、FPS、游戏内时间），下面是床与重生点管家的那一行，这里正在提示记录的床已经没了。右下角是盔甲耐久 HUD 的背包空格数。

![Freecam 自由视角](images/freecam.png)

Freecam：镜头离开身体自由飞，顶部提示条写着退出键和离身体多远。

![回到身体后看到的 Freecam 标记](images/freecam-marker.png)

回到身体后，粉色箭头和光柱带你去飞的时候标记的地方。

![Freecam 在黑暗的洞穴里](images/freecam-cave.png)

Freecam 飞的时候会自动照亮黑暗的洞穴。

## 使用方法

- 在游戏里按 **K** 或者用 [Mod Menu](https://modrinth.com/mod/modmenu) 就可以打开设置页面。
- 使用开关、滑条、或者点击选项按钮更改设置。
- 所有设置会自动保存。

**分享你的设置**
1. 点击界面底部的 **复制分享码**。所有开关和设置会变成一行可复制，以 `QOL1:` 开头的文字。
2. 点击 **导入分享码** 然后确认即可导入设置。



设置保存在 `config/qolbundle.json`。这个文件损坏时会使用默认值，并把坏文件另存为 `qolbundle.json.broken`。按存档记的东西会保存在 `config/qolbundle/worlds/` 里。

## 运行需求

| | 版本 |
|---|---|
| Minecraft | Java 版 1.21.11 |
| Fabric 加载器（Fabric Loader） | 0.19.5 或更新 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 必需（基于 0.141.6+1.21.11 构建） |
| Java | 21 或更新 |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 可选，在模组列表里加一个设置按钮（基于 17.0.1 构建） |
| QoL Bundle: X-ray add-on | 可选，单独的 jar；需要 QoL 全家桶（请用相同版本） |


## 兼容性

- **Sodium（钠）、Iris 和光影包：** 还没有完整测试过。如果画面有问题，最可能出在这些地方：放置大师、后视镜、Freecam 的地下画面，以及画在世界里的线和墙。
- **多人游戏：** 目前只在单人游戏里测试过。有些聊天相关的模块在装了特殊插件的服务器上可能表现不同。

## 安装

1. 为 Minecraft 1.21.11 安装 [Fabric 加载器](https://fabricmc.net/use/) 0.19.5 或更新版本。
2. 下载 1.21.11 版的 [Fabric API](https://modrinth.com/mod/fabric-api)，放进 `mods` 文件夹。
3. 下载 `qolbundle-0.1.0.jar`，放进同一个 `mods` 文件夹。
4. 可选：装 [Mod Menu](https://modrinth.com/mod/modmenu)，模组列表里就有设置按钮。
5. 可选，只有想要 X-ray 时才装：`qolbundle-xray-addon-0.1.0.jar`。
6. 启动游戏，进入世界后按 **K**。

## 致谢

- 作者：Autyism。
- 基于 [Fabric](https://fabricmc.net/) 加载器和 Fabric API；可选的设置按钮来自 TerraformersMC 的 [Mod Menu](https://modrinth.com/mod/modmenu)。
- 物品搜索用的拼音首字母表是用 [pypinyin](https://github.com/mozillazg/python-pinyin)（MIT 协议）生成的。

## 许可证

`GPL-3.0`。QoL 全家桶和 X-ray 附属包是以 GNU 通用公共许可证第 3 版发布的自由软件：你可以使用、分享和修改，但分发修改后的版本时必须使用同样的许可证。详见 [LICENSE](LICENSE)。
