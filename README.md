# friendservermenu

`friendservermenu` 是一个面向 Minecraft 好友服的轻量 Fabric MOD，提供游戏内自定义菜单，用于公共传送点、坐标分享、任务、活动、服务器状态和 OP 管理。

- MOD ID：`friendservermenu`
- 当前版本：`1.4.0`
- Minecraft：`1.21.11`
- Fabric Loader：`0.19.2+`
- Fabric API：`0.141.4+1.21.11`
- Java：21

客户端和服务端都需要安装本 MOD。客户端负责 GUI 和 HUD；服务端负责权限校验、传送、任务、活动、奖励和配置保存。

## 安装

1. 安装对应 Minecraft `1.21.11` 的 Fabric 客户端/服务端。
2. 将 Fabric API 放入 `mods` 文件夹。
3. 将 `friendservermenu-1.4.0.jar` 放入客户端和服务端的 `mods` 文件夹。
4. 启动游戏或服务器。

首次使用时，如果 `config/friendservermenu.json` 尚未初始化，OP 打开 `/menu` 会进入初始化界面，用于设置 GUI 左上角名称。

## 核心功能

- 菜单：`/menu` 或默认 `M` 键打开，记住上次所在主页面。
- 公共传送点：玩家可新增和编辑；创建者和 OP 可删除；服务端校验维度、坐标和 ID。
- 坐标：复制坐标、公开坐标、私发坐标、最近复制列表、死亡点记录。
- 坐标 HUD：显示维度、中文群系和坐标，支持开关、拖动和调整宽高。
- 任务：公开/私人任务、加入/退出、邀请、编辑、完成确认、历史任务。
- 任务奖励：OP 可配置奖励箱；任务完成后向参与者发放奖励，离线玩家下次进服补发。
- 任务 HUD：显示玩家已加入且进行中的任务，支持独立开关、拖动和调整宽高。
- 活动：OP 可发布集合、发物品、维护、探索/副本、自由通知等活动。
- 自动领取：玩家个人开关，只对“发物品”活动生效，仍走服务端去重校验。
- 服主管理：仅 OP 可见，包含服务器状态、活动管理、死亡点服务器级开关、时间、天气、玩家、清理、重载配置。

所有传送、奖励、任务、活动和管理操作都由服务端校验；客户端 payload 不被信任。

## 设置与权限

普通玩家设置页包含：

- 自动领取活动物品
- 坐标 HUD
- 任务 HUD

OP 服主管理页包含：

- 当前服务器活动状态
- 死亡点功能开关
- 死亡点聊天提示开关
- 活动管理
- 时间、天气、玩家、清理和配置重载

死亡点功能和死亡点聊天提示是服务器级开关。关闭死亡点功能后，服务端不再记录新死亡点，并清空当前已有死亡点。普通玩家不能通过伪造 payload 修改服务器级设置。

## 命令

| 命令 | 说明 |
|------|------|
| `/menu` | 打开菜单 |
| `/menu tasks` | 打开任务页 |
| `/menu activity` | 打开活动页 |
| `/adminmenu` | 打开服主管理页，仅 OP 可用 |
| `/fsm_coord_tp <id>` | 公开坐标聊天按钮使用的临时传送命令 |
| `/fsm_death_tp` | 死亡点聊天按钮使用的传送命令 |
| `/fsm_task_join <id>` | 任务邀请聊天按钮使用的加入命令 |
| `/fsm_activity_claim <id>` | 活动发物品聊天按钮使用的领取命令 |

`M` 键默认打开菜单，可在 Minecraft 按键绑定中修改。

## 配置文件

服务端配置：

```text
config/friendservermenu.json
config/friendservermenu-tasks.json
config/friendservermenu-death-points.json
config/friendservermenu-player-settings.json
config/friendservermenu-feature-settings.json
```

客户端 HUD 配置：

```text
config/friendservermenu-hud.json
config/friendservermenu-coordinate-hud.json
```

说明：

- `friendservermenu.json` 保存菜单标题、初始化状态和公共传送点。
- `friendservermenu-player-settings.json` 保存玩家个人设置，目前主要是自动领取活动物品。
- `friendservermenu-feature-settings.json` 保存服务器级开关，包括死亡点功能和死亡点聊天提示。
- 任务、死亡点和设置文件由服务端自动维护，不建议手动修改。

## 关键机制

- 死亡点：玩家死亡后记录最近死亡点，5 分钟内可传送；关闭死亡点功能会清空已有死亡点。
- 任务完成：当前任务至少 50% 成员确认完成后，任务完成并进入历史。
- 任务奖励：奖励箱物品会复制给参与者；背包放不下会掉落在玩家身边。
- 维护活动：维护倒计时结束后服务端执行停服。
- 发物品活动：OP 通过活动物品箱配置发放内容，手动领取和自动领取共用同一套服务端重复领取校验。
- 服务端驱动 UI：客户端会向服务器上报 UI 协议能力；支持时，传送、坐标、状态、设置和 OP 管理等页面可由服务端下发卡片、文字和按钮。新增客户端渲染能力、HUD 行为、键位或协议字段仍需要更新客户端。

## 构建

Windows PowerShell：

```powershell
.\gradlew.bat build
```

产物：

```text
build/libs/friendservermenu-1.4.0.jar
build/libs/friendservermenu-1.4.0-sources.jar
```

## 项目结构

```text
src/main/java/       服务端与通用逻辑
src/client/java/     客户端 GUI、HUD、网络接收
src/main/resources/  fabric.mod.json、声音资源、语言文件
```

## 不包含

`friendservermenu` 不是领地、经济、商店、权限组、反作弊、数据库或网页后台 MOD。它只提供好友服常用的轻量游戏内菜单。

## License

MIT
