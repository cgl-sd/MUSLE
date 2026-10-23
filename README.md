# 养肌宝

养肌宝是一款离线的个人训练记录 Android 应用。它通过固定角度的人体正面/背面图展示训练参与状态，支持动作记录、课程安排、训练历史、个人资料和成就。

- 当前版本：`0.3.0`
- 最低系统：Android 8.0（API 26）
- 应用 ID：`com.musclejournal.app`
- 数据默认仅保存在本机，不提供账户、云同步或线上数据上传

## 下载

- [GitHub 源码仓库](https://github.com/cgl-sd/MUSLE)
- [最新正式版](https://github.com/cgl-sd/MUSLE/releases/latest)
- [0.3.0 APK](https://github.com/cgl-sd/MUSLE/releases/download/v0.3.0/MuscleJournal-0.3.0.apk)
- [0.3.0 SHA-256](https://github.com/cgl-sd/MUSLE/releases/download/v0.3.0/MuscleJournal-0.3.0.apk.sha256)

下载后可以覆盖安装旧版本。更换签名证书会导致 Android 拒绝升级；需要保留数据时必须继续使用同一私钥签名。

## 更新

进入“个人 → 设置 → 检查更新”即可读取本仓库的最新正式 Release。发现新版本后由用户确认下载，应用会：

1. 只接受 `cgl-sd/MUSLE` 正式 Release 中的固定 APK 和 SHA-256 文件。
2. 校验 APK 大小、SHA-256、包名、版本名、递增的 versionCode 和当前安装签名。
3. 通过系统 `DownloadManager` 下载到应用私有外部目录。
4. 校验完成后调用系统安装器，由用户再次确认安装。

应用不会静默安装、不会绕过 Android 安装确认，也不会通过更新清空训练数据。检查更新需要设备能够访问 GitHub API 与 Release 文件托管域名。

## 当前功能

### 首页
- 固定正面/背面人体与精细肌群轮廓。
- 正面/背面/肌群三个独立 `56dp` 圆形按钮。
- 1–10 训练状态参考值，以及与已保存动作同源的肌群着色。
- 原位肌群标注、解剖介绍和相关动作。
- 当天真实训练记录与首页训练入口。

### 训练与课程
- 当天课程安排自动预选并置顶。
- 搜索、分类、加减动作与长按排序。
- 一次确认原子保存所记录动作，不伪造组数、重量或次数。
- 三个只读课程模板与单日课程安排。
- 训练记录按日、周、月查看，包含动作柱状图与补录。

### 个人与成就
- 本地身体资料、训练天数、动作数、本周训练天数。
- 20 枚基础成就，已获彩色、未获灰色。
- 动画开关、关于和检查更新。

## 数据与隐私

- 正式数据库为 SQLite，数据库迁移会保留既有训练事实。
- 不自动插入演示训练；debug 自动化使用独立测试数据库。
- 不提供本地备份、导出、导入或恢复入口。
- 不申请账户、通讯录或日历权限。
- 更新状态保存在 `no-backup` 目录，避免跨设备恢复出无效下载记录。

## 开发环境

项目按当前 Mac 的已有工具链配置：

- Android Studio `2026.1.3`
- Studio JBR Java `25.0.2`
- Android SDK API `37`，Build Tools `36.0.0`
- Gradle Wrapper `9.5.0`
- AGP `9.3.2`
- Kotlin/Compose compiler `2.2.10`

## 构建

### Debug

```bash
./src/scripts/build-debug.sh
```

输出：

```text
tmp/build/app/outputs/apk/debug/app-debug.apk
```

### 单元测试与静态检查

```bash
./gradlew :app:testDebugUnitTest --offline
./gradlew :app:lintDebug --offline
```

### Release

```bash
./src/scripts/build-release.sh
```

脚本会离线构建 Release、执行 `zipalign`、使用本机密钥签名、验证签名/对齐并生成 APK 与 `.sha256`：

```text
tmp/release/MuscleJournal-0.3.0.apk
tmp/release/MuscleJournal-0.3.0.apk.sha256
```

签名环境变量：

```bash
export MUSCLEJOURNAL_KEYSTORE=/path/to/private.keystore
export MUSCLEJOURNAL_STORE_PASSWORD=...
export MUSCLEJOURNAL_KEY_ALIAS=...
export MUSCLEJOURNAL_KEY_PASSWORD=...
```

未设置时脚本使用本机 `~/.android/debug.keystore`，用于兼容当前个人侧载安装。该证书不属于商店发布身份；后续更新必须保留同一私钥。密钥文件和密码不会提交到仓库。

## 设备运行

本项目专用 AVD 为 `MuscleJournal_API37`。操作前先核验 AVD 名称，序列号可能变化：

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" devices -l
"$ADB" -s <serial> emu avd name
"$ADB" -s <serial> install -r tmp/build/app/outputs/apk/debug/app-debug.apk
"$ADB" -s <serial> shell am force-stop com.musclejournal.app
"$ADB" -s <serial> shell am start -W -n com.musclejournal.app/.MainActivity
```

不要使用相邻 PitPitch 项目的模拟器，也不要通过卸载或清空数据规避安装问题。

## 目录

```text
app/                              Android app 模块
src/main/                         原生 Kotlin、资源、Manifest 与资产
src/scripts/                      构建和发布脚本
tmp/                              构建、测试、日志与验证证据（忽略）
docs/                             开发文档与设计资料（忽略）
README.md                         项目介绍、下载、构建与更新说明
AGENTS.md                         持续开发约束
CHANGELOG.md                      按日期记录变更和验证结果
```

公开仓库不包含大型设计资料。完整的本机开发计划、验收记录和资产来源位于 `docs/DEVELOPMENT_PLAN.md`，该目录按项目规则保留在本地。

## 已知限制

- 仅处理 GitHub 正式 Release，不读取预发布或草稿。
- 更新需要可连接 GitHub 的网络；失败不会删除已安装应用或用户数据。
- 当前没有云端账户、跨设备同步或远程恢复。
- API 26 兼容结论沿用历史约束，最近验收在 API 37 专用模拟器完成。
