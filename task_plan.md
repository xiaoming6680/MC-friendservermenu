# 任务计划：FriendServerMenu Fabric MOD

## 目标
创建并持续优化一个可编译的 Minecraft Java Edition 1.21.11 Fabric MOD：FriendServerMenu，包含客户端自定义“小铭的服务器菜单”GUI、服务端命令与权限校验、Fabric networking、JSON 公共地点配置、README 和构建验证。

## 当前阶段
阶段 17 完成

## 各阶段

### 阶段 1：需求与发现
- [x] 读取用户粘贴需求
- [x] 确认当前项目目录为空，需要从零创建 Gradle/Fabric 项目
- [x] 将需求与约束记录到 findings.md
- **状态：** complete

### 阶段 2：版本与技术方案
- [x] 查询并确定 Minecraft 1.21.11 可用的 Yarn、Fabric Loader、Fabric API、Loom/Gradle 版本
- [x] 确定包名、项目结构、网络 payload 和服务端/客户端边界
- [x] 记录关键决策及理由
- **状态：** complete

### 阶段 3：项目骨架
- [x] 创建 Gradle 配置、fabric.mod.json 和资源目录
- [x] 创建 common/server/client 源码目录
- [x] 确保服务端入口不引用客户端专属类
- **状态：** complete

### 阶段 4：核心功能实现
- [x] 实现 /menu 和 /adminmenu
- [x] 实现 JSON 配置生成、读取和重载
- [x] 实现 Fabric payload：OpenMenuPayload、MenuDataPayload、MenuActionPayload、ServerStatusPayload
- [x] 实现传送、坐标广播/私发、活动集合、状态采集和 OP 管理动作
- **状态：** complete

### 阶段 5：客户端 GUI
- [x] 实现 FriendMenuScreen 自定义 Screen
- [x] 左侧分页：传送、坐标、活动、状态、服主管理
- [x] 管理分页只对 OP 2+ 的菜单数据可见
- [x] 卡片式按钮、深色半透明背景、悬停高亮
- **状态：** complete

### 阶段 6：文档与验证
- [x] 编写 README.md
- [x] 运行 Gradle 构建
- [x] 修复编译或映射问题
- [x] 记录构建命令、结果和产物
- **状态：** complete

## 关键问题
1. Minecraft 1.21.11 对应版本已确认：Yarn `1.21.11+build.6`、Loader `0.19.2`、Fabric API `0.141.4+1.21.11`。
2. 1.21.11 Yarn 的权限和客户端输入 API 与旧写法不同，已按本地映射修正并构建通过。

## 已做决策
| 决策 | 理由 |
|------|------|
| 从空目录创建完整 Gradle 项目 | 当前工作目录没有现有源文件 |
| 包名暂定为 `com.xm6680.friendservermenu` | 与 MOD 名称对应，避免默认示例包名 |
| 规划文件放在项目根目录 | 符合 planning-with-files-zh 要求 |
| 使用 Yarn `1.21.11+build.6`、Loader `0.19.2`、Fabric API `0.141.4+1.21.11`、Loom `1.14.10` | 官方 Fabric Maven 元数据可解析；Loom 1.14 系列符合 Fabric 1.21.11 官方文章建议且兼容 Gradle 8 |
| 生成 Gradle Wrapper，版本使用 `9.2.0` | 本机没有全局 Gradle 命令；Loom `1.14.10` 要求 Gradle plugin API `9.2.0` |

## 遇到的错误
| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| PowerShell 初次读取中文时出现乱码 | 1 | 设置 UTF-8 输出后重新读取 |
| 本机缺少全局 `gradle` 命令 | 1 | 通过临时下载 Gradle 分发包生成 Wrapper |
| Loom `1.16.2` 与 Gradle `8.14.3` 不兼容 | 1 | 改为 Loom `1.14.10` |
| Loom `1.14.10` 与 Gradle `8.14.3` 不兼容 | 1 | 改用 Gradle `9.2.0` |
| `FAIL_ON_PROJECT_REPOS` 阻止 Loom 添加本地 remap 仓库 | 1 | 移除 settings 中的仓库限制 |
| 1.21.11 Yarn 中 `hasPermissionLevel/getServer/getServerWorld` 不存在 | 2 | 根据本地映射改用 `DefaultPermissions.GAMEMASTERS`、`ServerCommandSource#getServer()`、`getEntityWorld()` |
| 1.21.11 client GUI 鼠标事件签名变化 | 1 | 改用 `mouseClicked(Click, boolean)` |
| 本机缺少 `git` 命令 | 1 | 跳过 git 状态检查，使用文件列表和构建验证确认交付 |

## 验证结果
| 验证 | 结果 |
|------|------|
| `.\gradlew.bat build` | 成功 |
| 产物 | `build/libs/friendservermenu-1.0.0.jar` |
| jar 内 `fabric.mod.json` | `version` 为 `1.0.0` |
| 服务端代码客户端引用检查 | `rg "client\\.gui|MinecraftClient|net\\.minecraft\\.client" src\\main\\java` 无匹配 |

## 备注
- 每完成一个阶段更新本文件状态。
- 所有实现必须保持客户端/服务端类边界清晰。
- 客户端只发送 action id 或 location id，服务端读取真实坐标、权限和配置。

## 追加阶段 7：`/menu` 崩溃修复
- [x] 解压并读取用户提供的崩溃报告 `错误报告-2026-5-31_19.15.01.zip`
- [x] 定位崩溃堆栈：`FriendMenuScreen.render` 调用 `renderBackground` 导致 1.21.11 同一帧重复 blur
- [x] 修改 GUI 背景绘制，避免触发第二次 blur
- [x] 运行 Gradle 构建并核对产物
- **状态：** complete

## 追加阶段 8：GUI 与菜单逻辑增量优化
- [x] 标题与默认配置改为“小铭的服务器菜单”，默认 `locations` 改为空数组
- [x] 增加实时刷新 payload，GUI 打开后每 1 秒请求菜单数据与状态，关闭后停止
- [x] 缩小并自适应 GUI，增加左侧导航和右侧内容独立滚动
- [x] 增加自定义 `friendservermenu:gui_click` 音效事件与统一按钮点击播放方法
- [x] 传送页面新增“新增传送点”二级页，普通玩家可提交，服务端保存并同步
- [x] 坐标页面改为三个紧凑按钮：复制坐标、公开坐标、私发坐标
- [x] 坐标页面三个按钮点击后自动关闭 GUI
- [x] 活动页面改为 OP 模板，服务端校验 OP，记录活动集合坐标并支持接受传送
- [x] 更新 README 与规划文件
- [x] 运行构建、核对 jar 与元数据
- **状态：** complete

## 追加阶段 9：GUI 文字修复、传送点编辑与管理扩展
- [x] 修复 GUI 中多处文字消失/被控件覆盖的问题
- [x] 传送页为 OP 增加已保存传送点的编辑、删除按钮
- [x] 增加传送点编辑二级页，布局复用新增传送点页面
- [x] 普通玩家新增传送点时隐藏 ID 输入，服务端自动保证唯一 ID
- [x] 维度从手动输入改为下拉式菜单
- [x] 状态页世界时间显示为 24H 制
- [x] 广播坐标聊天文字后追加绿色 `[点我传送]` 按钮
- [x] 腐竹管理页增加更多功能，并把时间/天气/清理/传送等功能拆入二级菜单
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 10：GUI 按钮文字透明修复
- [x] 复查客户端 GUI 文本绘制颜色
- [x] 修复 `MenuButton` 标题颜色缺少 alpha 通道的问题
- [x] 搜索客户端 GUI 源码，确认不再存在 6 位 RGB 文本颜色常量
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 11：新增传送点表单反馈与退出按钮
- [x] 修复中文传送点名称生成 ID 固定退化的问题
- [x] 新增服务端保存结果回包，失败显示在提交按钮旁，成功返回传送页
- [x] “使用当前位置”后在按钮右侧显示坐标已填入提示
- [x] 优化新增/编辑传送点页面排版，将 X/Y/Z 的 -1/+1 放在输入框旁
- [x] 为 GUI 增加退出按钮
- [x] 对传送、活动、管理等一次性动作点击后关闭 GUI
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 12：应用自定义按钮点击音效
- [x] 将用户新增的 `Button.ogg` 移到 Fabric 声音资源目录
- [x] 将 `sounds.json` 移到 `assets/friendservermenu/sounds.json`
- [x] 将 `friendservermenu:gui_click` 映射到 `friendservermenu:button`
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar 中的音效资源
- **状态：** complete

## 追加阶段 13：按钮音效、活动模板、传送编辑权限与腐竹玩家管理
- [x] 修复 GUI 按钮点击不播放自定义音效的问题
- [x] 活动组织页面增加模板分类，支持发物品等通知模板
- [x] 将活动倒计时改为可开关的结束日期，并优化聊天栏排版
- [x] 允许普通玩家编辑公共传送点，删除仍仅 OP 可用，服务端二次校验
- [x] 腐竹管理玩家二级菜单增加游戏模式、飞行、生命/饥饿、kick/ban、OP 授权/撤销等功能
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 14：菜单标题改名
- [x] 将默认菜单标题从“小铭的控制台”改为“小铭的服务器菜单”
- [x] 同步客户端 Screen 兜底标题、服务端配置默认值、README 和音效字幕
- [x] 旧配置标题“朋友服控制台”或“小铭的控制台”自动迁移为新标题
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 15：按钮音效编码与活动维护倒计时优化
- [x] 将按钮音效从 Ogg FLAC 转为 Minecraft 可读取的 Ogg Vorbis
- [x] 活动聊天标题从“小铭活动通知”改为“活动通知”
- [x] 发物品、维护等不需要集合的模板隐藏集合地点和传送开关
- [x] 维护模板增加维护时间和维护倒计时
- [x] 服务端维护倒计时在 60/30/15/10/5/4/3/2/1 秒提醒，到点执行 `/stop`
- [x] 活动界面实时显示维护倒计时并优化排版
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 16：坐标提示、活动结束与玩家管理二级页
- [x] 坐标页按钮悬停时在按钮下方显示作用说明
- [x] 活动页增加结束/提前结束按钮并由服务端校验 OP 权限
- [x] 活动聊天里的 `[打开菜单前往集合点]` 改为蓝色并打开活动页
- [x] `/menu` 支持指定初始页面参数
- [x] 玩家管理页改为先列玩家名，点击进入单玩家操作二级页
- [x] 单玩家操作增加传送、清效果、熄灭等功能，OP 授权支持选择 1-4 级
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar
- **状态：** complete

## 追加阶段 17：发物品活动手动领取与离线玩家一次性通知
- [x] 发物品活动发布后不再自动发给在线玩家
- [x] 聊天栏添加 `[手动领取]` 按钮并由服务端校验活动 ID
- [x] 活动 GUI 添加“领取物品”按钮并显示已领取状态
- [x] 服务端记录每个玩家是否已领取，防止重复领取
- [x] 玩家离线期间发布的活动在加入服务器后聊天提示一次
- [x] 更新 README、findings、progress
- [x] 运行构建并核对 jar
- **状态：** complete
