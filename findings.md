# 发现与决策

## 需求
- MOD 名称：FriendServerMenu。
- 目标版本：Minecraft Java Edition 1.21.11，Fabric，Java。
- 同时安装在客户端和服务端。
- 玩家执行 `/menu` 后打开真正的客户端自定义 GUI，不使用箱子 GUI。
- GUI 标题为“朋友服控制台”，深色半透明背景，左侧分页，右侧内容区，卡片式按钮，悬停高亮。
- 分页：传送、坐标、活动、状态、服主管理；服主管理仅 OP 等级 2 及以上可见。
- 服务端负责真实操作：传送、天气、时间、清理实体、权限检查、配置读取。
- 客户端点击按钮时只发送 action id 或 location id，不能发送可信坐标或权限。
- 需要 `/menu` 与 `/adminmenu` 命令。
- 需要 JSON 配置文件，保存公共地点列表和菜单标题。
- README 需要说明构建、安装、配置、命令和 OP 权限功能。

## 研究发现
- 当前项目目录为空，需要从零搭建 Gradle/Fabric 项目。
- 用户粘贴文本在默认 PowerShell 输出中出现乱码，使用 UTF-8 输出后已正确读取。
- Fabric 官方 1.21.11 文章确认该版本可用于 Fabric 开发，并在发布时建议使用 Loom 1.14、Fabric Loader 最新稳定版。
- Fabric Maven 元数据当前显示：Yarn `1.21.11+build.6`，Fabric Loader `0.19.2`，Fabric API 最新 1.21.11 构建 `0.141.4+1.21.11`。
- Loom Maven 元数据当前最新包含 alpha 版本；初选最新非 alpha `1.16.2` 时发现它要求 Gradle plugin API `9.4.0`，改用 Fabric 1.21.11 官方文章建议的 1.14 系列非 alpha 版本 `1.14.10` 后，确认它要求 Gradle plugin API `9.2.0`。
- 本机 Java 为 21.0.10，可满足 Minecraft 1.21.x / Fabric 开发；本机没有全局 `gradle` 命令，需要生成 Gradle Wrapper。
- `.\gradlew.bat build` 最终构建成功，生成 `build/libs/friendservermenu-1.0.0.jar`。
- jar 内 `fabric.mod.json` 的 `version` 已展开为 `1.0.0`。
- `src/main/java` 未引用 `MinecraftClient`、`net.minecraft.client` 或客户端 GUI 包，服务端边界符合要求。

## 技术决策
| 决策 | 理由 |
|------|------|
| 包名使用 `com.xm6680.friendservermenu` | 与项目名匹配，避免冲突 |
| 使用 Fabric custom payload networking | 满足客户端 Screen 与服务端动作通信要求 |
| 服务端入口只注册命令、配置和服务端 payload handler | 防止服务端引用客户端专属类 |
| 客户端入口只注册客户端 payload handler 和打开 Screen | 保持 common/server 与 client 分离 |
| 不添加 mixin 配置 | 当前需求不需要修改原版行为 |
| 使用 Gradle Wrapper，Wrapper 版本为 `9.2.0` | 本机没有全局 Gradle；Loom `1.14.10` 要求 Gradle plugin API `9.2.0` |
| 使用 Loom `1.14.10` | Loom `1.16.2` 要求 Gradle plugin API `9.4.0`；`1.14.10` 与官方 1.21.11 文章建议的 1.14 系列一致 |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| 中文内容初次读取乱码 | 用 UTF-8 输出重新读取 |
| 本机缺少全局 `gradle` 命令 | 后续生成并使用 Gradle Wrapper |
| Loom `1.16.2` 与 Gradle `8.14.3` 不兼容 | 将 Loom 调整为 `1.14.10` |
| Loom `1.14.10` 与 Gradle `8.14.3` 不兼容 | 改用 Gradle Wrapper `9.2.0` |
| `settings.gradle` 的 `FAIL_ON_PROJECT_REPOS` 阻止 Loom 添加 `LoomLocalRemappedMods` | 移除该限制并在 `build.gradle` 中声明仓库 |
| 1.21.11 Yarn 中权限和玩家服务器方法名不同于常见 1.21.x 写法 | 使用 `net.minecraft.command.DefaultPermissions.GAMEMASTERS` 与 `ServerCommandSource#getPermissions()` 做 OP 2+ 检查；使用 `ServerCommandSource#getServer()` 取得服务器实例 |
| 1.21.11 客户端 GUI 输入 API 使用 `net.minecraft.client.gui.Click` | `Screen#mouseClicked` 需要覆写 `mouseClicked(Click, boolean)` |
| 最终构建产物 | `build/libs/friendservermenu-1.0.0.jar` |

## 资源
- 用户粘贴需求文件：`C:\Users\xiaoming6680\.codex\attachments\cb1187de-30de-46aa-915f-1c3fbaf92200\pasted-text.txt`
- Fabric 1.21.11 官方文章：https://fabricmc.net/2025/12/05/12111.html
- Fabric Maven 元数据：https://maven.fabricmc.net/

## 视觉/浏览器发现
- 暂无。

## 2026-05-31 `/menu` 崩溃诊断
- 用户提供的崩溃包 `错误报告-2026-5-31_19.15.01.zip` 中，`crash-2026-05-31_19.14.55-client.txt` 显示崩溃发生在客户端渲染线程，描述为 `Rendering screen`。
- 直接异常为 `java.lang.IllegalStateException: Can only blur once per frame`。
- 堆栈落点：`com.xm6680.friendservermenu.client.gui.FriendMenuScreen.method_25394(FriendMenuScreen.java:53)`，对应 `FriendMenuScreen.render` 内调用 `renderBackground(context, mouseX, mouseY, delta)`。
- 1.21.11/Fabric Screen API 会在屏幕渲染包装流程里先处理屏幕背景/blur；自定义 Screen 再调用 `renderBackground` 会触发同一帧第二次 blur。
- 修复策略：不调用 `renderBackground`，改为直接绘制已有半透明遮罩和菜单面板，保留视觉效果并避免重复 blur。
- 验证结果：`rg "renderBackground\\(" src\\client\\java\\com\\xm6680\\friendservermenu\\client\\gui\\FriendMenuScreen.java` 无匹配；`.\gradlew.bat build` 成功；产物为 `build/libs/friendservermenu-1.0.0.jar`。

## 2026-05-31 GUI 与菜单逻辑优化发现
- `ModConfig.defaults()` 原先会自动写入 `main_base`、`mob_farm`、`nether_hub` 示例传送点；本轮按需求改为默认空 `locations`。
- 菜单标题来源包括 `OpenMenuPayload`、`MenuDataPayload`、客户端 Screen 构造兜底和配置默认值；当前统一为“小铭的服务器菜单”，并在读取旧配置标题“朋友服控制台”或“小铭的控制台”时自动迁移到新标题。
- 实时刷新采用独立 `RequestMenuDataPayload`，客户端 GUI 打开时每 20 tick 请求一次，服务端返回 `MenuDataPayload` 和 `ServerStatusPayload`。
- 新增传送点采用 `AddLocationPayload` 提交待创建数据；服务端负责校验名称、ID、重复 ID、维度是否加载、坐标和朝向是否为有限数字，并只保存通过校验的数据。
- 活动页改为 OP 模板：客户端提交活动文本，服务端再次校验 OP 权限，并以发起人服务端当前位置记录集合坐标；普通玩家只能看活动通知和可用的“前往集合点”按钮。
- 坐标页按钮缩小为“复制坐标 / 公开坐标 / 私发坐标”，点击后统一播放自定义点击音并关闭 GUI。
- 自定义点击音注册为 `friendservermenu:gui_click`，`sounds.json` 当前保留空 `sounds` 数组，后续可补 `assets/friendservermenu/sounds/gui_click.ogg`。
- 第一次编译发现 `PositionedSoundInstance.master(SoundEvent,float)` 在 1.21.11 Yarn 中不存在；根据映射改用 `PositionedSoundInstance.ui(SoundEvent,float,float)` 后 `compileJava compileClientJava` 通过。
- 本机没有 `ffmpeg` 命令，未生成实际 ogg 文件；当前交付保留自定义 SoundEvent、统一播放入口和 `sounds.json` 资源占位。

## 2026-05-31 GUI 文字修复与管理扩展发现
- GUI 文字消失的原因是页面内容先绘制文字，随后再统一绘制输入框和按钮；输入框/按钮背景会覆盖同一区域已绘制的标签或说明。本轮改为在页面布局创建控件时即时绘制控件，保留滚动裁剪并避免后绘制控件盖住文字。
- 传送点新增页和编辑页共用同一套表单布局；OP 可以看到并修改传送点 ID，普通玩家看不到 ID 输入。
- 普通玩家新增公共传送点时，服务端不信任客户端传来的 ID，而是根据名称生成唯一 ID；OP 留空 ID 时也会自动生成。
- 传送点编辑和删除新增独立 payload，服务端再次检查 OP 权限，并通过配置管理器校验世界、坐标、重复 ID 与保存结果。
- 维度选择从文本框改为 GUI 内下拉选项：主世界、下界、末地；服务端仍按维度 ID 校验对应世界是否加载。
- 状态页仍接收服务端原始 Minecraft day time tick，但客户端显示时换算为 24H：`(timeOfDay + 6000) % 24000` 对应现实 00:00-23:59。
- 公开坐标聊天按钮采用服务端临时坐标分享 ID，点击绿色 `[点我传送]` 执行 `/fsm_coord_tp <id>`；实际维度和坐标由服务端保存，5 分钟后失效。
- 腐竹管理页拆为时间、天气、玩家、清理四个二级菜单，并新增正午、午夜、雷暴、全员恢复生命、全员补满饥饿、清理敌对生物等操作。
- 本轮 `.\gradlew.bat build` 成功；产物仍为 `build/libs/friendservermenu-1.0.0.jar`，jar 内 `fabric.mod.json` 版本为 `1.0.0`。

## 2026-05-31 GUI 按钮文字透明修复发现
- 继续排查“部分文字不显示”时发现 `MenuButton` 的按钮标题颜色仍使用 6 位 RGB `0xFFFFFF`，没有显式 alpha 通道；其他 GUI 文本大多已经使用 `0xFF...` ARGB。
- 本轮将按钮标题颜色改为 `0xFFFFFFFF`，并把标题/描述颜色提为常量，避免不同渲染管线或文本层把缺少 alpha 的颜色当成透明。
- `rg "0xFFFFFF([^0-9A-Fa-f]|$)|0x[0-9A-Fa-f]{6}([^0-9A-Fa-f]|$)" src/client/java/com/xm6680/friendservermenu/client/gui` 已无匹配。
- `.\gradlew.bat build` 成功；产物为 `build/libs/friendservermenu-1.0.0.jar`，jar 内 `fabric.mod.json` 版本为 `1.0.0`。

## 2026-05-31 新增传送点表单反馈发现
- 中文传送点名称原先会被 ID 正则清空，客户端退回 `new_location`，服务端退回 `location`，容易造成重复或看起来没有正常生成。本轮改为：英文数字保留为 slug，纯中文或其他非 ASCII 名称生成 `tp_<hash>`。
- 新增 `LocationMutationResultPayload` 作为服务端新增/编辑/删除传送点结果回包；客户端失败时留在表单页并在提交按钮旁显示错误，成功时返回传送页。
- 新增/编辑传送点页面将 X/Y/Z 输入框拆成三行，每行输入框右侧直接放 `-1` / `+1` 微调按钮，避免下面另起一组按钮占空间。
- `使用当前位置` 点击后会在按钮右侧短暂显示“已经将当前坐标填入！”。
- GUI 右上角新增 `×` 退出按钮；传送、活动传送、活动通知和服主管理类一次性动作发送后会自动关闭 GUI。
- 本轮 `.\gradlew.bat build` 成功；产物为 `build/libs/friendservermenu-1.0.0.jar`，jar 内 `fabric.mod.json` 版本为 `1.0.0`。

## 2026-05-31 自定义按钮点击音效应用发现
- 用户新增的音效文件位于 `src/main/resources/Button.ogg`；Fabric/Minecraft 声音资源需要放在 `assets/<namespace>/sounds/` 下，并使用小写资源路径。
- 本轮将音效移动为 `src/main/resources/assets/friendservermenu/sounds/button.ogg`。
- `sounds.json` 从资源根目录移动到 `src/main/resources/assets/friendservermenu/sounds.json`，并将 `gui_click` 映射到 `friendservermenu:button`。
- `.\gradlew.bat build` 成功；jar 内已包含 `assets/friendservermenu/sounds.json` 与 `assets/friendservermenu/sounds/button.ogg`。

## 2026-05-31 活动、传送点编辑权限与玩家管理扩展发现
- 按钮点击入口 `FriendMenuScreen#playClickSound` 已统一调用自定义 `friendservermenu:gui_click`，资源也已在 jar 内；为降低客户端侧 SoundEvent 注册或 UI 声音实例差异导致的静音概率，本轮改为客户端兜底注册并优先通过本地玩家播放自定义声音。
- 活动 payload 目前用 JSON 字符串承载模板数据，因此可兼容扩展 `category`、`hasEndDate`、`endDateText`、`itemId`、`itemCount` 等字段，不需要新增 payload 类型。
- 活动服务端原先只支持 `countdownSeconds`，并把活动有效期设为倒计时加 10 分钟；本轮改为可选结束日期，关闭时活动不按日期自动过期，新的活动会覆盖旧活动。
- 传送点编辑服务端原先强制要求 OP；本轮要允许普通玩家编辑，但普通玩家不能改 ID，服务端会用原 ID 覆盖客户端提交的 ID。
- 玩家管理页当前只有全员传送、全员回血、全员补饥饿；状态数据需要增加在线玩家名列表，GUI 才能按玩家生成游戏模式、飞行、回血、补饥饿、kick、ban、OP 操作按钮。
- 发物品活动模板由服务端验证物品 ID 与 1-64 数量后，给所有在线玩家发放物品；如果背包无法插入，物品会掉落到玩家身边。
- 临时飞行使用服务端内存记录 10 分钟到期时间，并通过 `ServerTickEvents.END_SERVER_TICK` 到期撤销；创造和旁观模式不会被撤销飞行能力。
- 本轮 `.\gradlew.bat compileJava compileClientJava` 和 `.\gradlew.bat build` 均成功；产物为 `build/libs/friendservermenu-1.0.0.jar`，jar 内 `fabric.mod.json` 版本为 `1.0.0`，并包含 `assets/friendservermenu/sounds/button.ogg`。

## 2026-05-31 菜单标题改名发现
- 当前默认菜单标题已改为“小铭的服务器菜单”，修改点包括 `ModConfig` 默认字段、`ModConfigManager.DEFAULT_MENU_TITLE` 和 `FriendMenuScreen.DEFAULT_TITLE`。
- 为兼容已有服务器配置，读取到旧标题“朋友服控制台”或“小铭的控制台”时会自动迁移为“小铭的服务器菜单”。
- README 示例配置和 `/menu` 描述已同步新标题，按钮音效字幕也改为“小铭的服务器菜单按钮”。

## 2026-05-31 音效编码与维护倒计时发现
- `src/main/resources/assets/friendservermenu/sounds/button.ogg` 原文件是 Ogg 容器里的 FLAC 编码，`ffprobe` 显示 `codec_name=flac`；Minecraft 普通 `sounds.json` 声音资源通常需要 Ogg Vorbis，因此路径正确也可能静音。
- 本机可用 `D:\RVC1006Nvidia\ffmpeg.exe`，已将 `button.ogg` 转为 Vorbis，`ffprobe` 显示 `codec_name=vorbis`、`sample_rate=44100`、`duration=0.905578`。
- 活动页当前所有模板都会显示集合地点和传送开关；发物品、维护这类不需要集合的模板需要在 GUI 和服务端验证中都跳过集合字段。
- 维护倒计时必须由服务端持有结束时间并在 tick 中提醒和执行停服，不能只依赖客户端 GUI 倒计时。
- 活动聊天标题已统一为“活动通知”；发物品和维护模板在 GUI 中不再渲染集合地点与传送开关，服务端也会忽略这两类模板提交的集合/传送字段。
- 维护模板提交后会保存 `maintenanceEndsAtMillis`，并在菜单数据中同步 `maintenanceRemainingSeconds`；客户端活动页在两次服务端刷新之间本地递减，避免客户端本机时间和服务器时间不一致时倒计时偏差过大。
- 维护倒计时服务端提醒使用 `<=` 判断，避免服务器卡顿跳过精确秒数时漏发 60/30/15/10/5/4/3/2/1 秒提醒。
- 本轮 `.\gradlew.bat compileJava compileClientJava` 与 `.\gradlew.bat build` 均成功；产物为 `build/libs/friendservermenu-1.0.0.jar`，jar 内包含音效资源、`FriendMenuScreen.class` 和 `ActivityManager.class`，`fabric.mod.json` 版本为 `1.0.0`。

## 2026-05-31 坐标提示、活动结束与玩家管理二级页发现
- 坐标页按钮目前只有悬停高亮，没有专门的作用说明；按钮本身高度较小，不适合直接塞第二行说明，适合在按钮组下方单独绘制悬停提示。
- 活动通知聊天里的 `[打开菜单前往集合点]` 当前点击执行 `/menu`，服务端默认打开传送页；需要让 `/menu` 支持页面参数后改为 `/menu activity`。
- 活动结束应由服务端持有并清空 `activeActivity`，客户端按钮只能发送 action，服务端仍需检查 OP 2+ 权限。
- 玩家管理页当前把每个玩家的全部操作直接铺在列表里，内容过密；改为玩家列表页只显示玩家名，详情页按操作类别分组更清楚。
- 当前 OP 授权服务端固定给 OP 2；本轮需要把按钮参数里的 1-4 级解析成 `PermissionLevel.fromLevel(level)`，再交给 `LeveledPermissionPredicate.fromLevel`。
- 坐标页悬停提示已改为按钮组下方独立文本，避免塞进 26px 高的小按钮导致文字被裁剪。
- `/menu` 已支持可选页面参数，活动聊天按钮改为蓝色 AQUA 并执行 `/menu activity`，会直接打开活动页。
- 活动页 OP 侧会根据是否有结束日期显示“提前结束活动”或“结束活动”；服务端 `ActivityManager.endActivity` 会再次检查 OP 权限和活动 ID。
- 玩家管理页已改为“在线玩家管理”只显示玩家名；`ADMIN_PLAYER_DETAIL` 二级页按传送、游戏模式、状态、权限、处罚分组显示操作。
- 单玩家新增了“传到我”“我去找TA”“清效果”“熄灭”，OP 授权按钮改为 OP1/OP2/OP3/OP4；服务端会解析并夹紧到 1-4 级。
- 本轮 `.\gradlew.bat build` 成功；产物为 `build/libs/friendservermenu-1.0.0.jar`，jar 内 `fabric.mod.json` 版本为 `1.0.0`，并包含本轮涉及的 GUI、活动、玩家管理和 `/menu` 命令类。

## 2026-05-31 发物品活动手动领取发现
- 当前发物品模板在 `ActivityManager.submitActivity` 中调用 `giveItemsToOnlinePlayers`，发布瞬间自动发给所有在线玩家；这需要改成只保存可领取活动。
- 聊天栏按钮如果要直接领取，需要一个服务端命令作为 `ClickEvent.RunCommand` 目标；GUI 可以继续用 `MenuActionPayload`。
- 活动 GUI 当前收到的是全局 `activeActivityJson()`，若要显示“领取物品/已领取”，需要按查看玩家生成活动 JSON。
- “离线玩家加入后也能看见，但只显示一次”适合在服务端活动对象中记录已提示玩家 UUID；在线广播和加入提示都走同一个发送逻辑，加入时未提示过才补发一次聊天通知。
- 发物品活动已改为发布后只广播可领取通知，不再自动给在线玩家发物品；聊天按钮执行 `/fsm_activity_claim <活动ID>`，GUI 按钮发送 `activity_claim_item`。
- 服务端 `ActivityManager.claimItem` 会验证活动存在、类型为发物品、物品 ID 仍有效、玩家未领取过；领取成功后记录玩家 UUID，背包放不下时掉落物品。
- `MenuDataPayload` 的活动 JSON 改为按玩家生成，发物品活动会带 `itemClaimedByViewer`，客户端据此显示“领取物品”或“你已领取本次活动物品”。
- `ServerPlayConnectionEvents.JOIN` 会在玩家加入时检查当前活动，未提示过的玩家会收到一次活动聊天通知；已在线收到过广播或已加入提示过的玩家不会重复收到。
- 本轮 `.\gradlew.bat compileJava compileClientJava` 与 `.\gradlew.bat build` 均成功；产物为 `build/libs/friendservermenu-1.0.0.jar`，jar 内包含 `ActivityClaimCommand.class`、`ActivityManager.class`、`ModNetworking.class` 和 `FriendMenuScreen.class`，`fabric.mod.json` 版本为 `1.0.0`。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
