# 养肌宝

养肌宝是面向个人的 Android 训练记录应用。记录做过的动作、安排课程，回顾自己的训练历程。

- [下载 Android 安装包](https://github.com/cgl-sd/MUSLE/releases/download/v0.4.0/MuscleJournal-0.4.0.apk)

支持 Android 8.0 及以上。

![养肌宝：让每次训练都有迹可循](artwork/readme/cover.png)

## 功能

**身体探索**：直观感受训练的肌群，记录训练的变化。

**动作记录**：搜索、分类选择动作，支持增减与长按排序，一次保存本次记录；单侧动作可选择左侧或右侧。

**课程安排**：提供上肢力量、下肢力量和全身基础模板，支持调整动作与安排日期；当天安排自动预选，完成记录后同步安排状态。

**训练历史**：按日、周、月查看动作记录与图表。

**个人与成就**：保存身体资料，查看训练天数与达成的成就。

[![养肌宝界面预览：身体探索、动作记录与课程安排](artwork/readme/screens.png)](artwork/readme/screens.png)

> Android 开发版界面；安排内容来自隔离测试数据。

## 下载与安装

1. 下载 [MuscleJournal-0.4.0.apk](https://github.com/cgl-sd/MUSLE/releases/download/v0.4.0/MuscleJournal-0.4.0.apk) 和对应的 [SHA-256 校验文件](https://github.com/cgl-sd/MUSLE/releases/download/v0.4.0/MuscleJournal-0.4.0.apk.sha256)。
2. 按 Android 提示允许当前来源安装，并确认安装。应用尚未上架应用商店。
3. 旧版可直接覆盖安装，保留本地记录；包名和签名证书必须一致。

“个人 → 设置 → 检查更新”读取本仓库的 GitHub 正式 Release。经用户确认后下载，核验大小、SHA-256、包名、版本与签名，再交由 Android 系统安装器确认。

## 数据与隐私

训练记录、课程安排和身体资料保存在应用私有 SQLite 数据库，仅更新变化条目，较大内容按需无损压缩。旧记录保留兼容，保存训练与同步安排使用同一事务。

应用不提供账户、云同步、数据上传或本地备份功能。卸载应用或清除应用数据会丢失本地记录，请保留应用数据并使用覆盖安装升级。仅检查更新与下载新版需要联网。

人体着色与 1–10 训练状态是依据已保存动作和时间计算的产品参考，不是生理疲劳测量或医学结论。

## 资源

人体、头像与启动图标为本项目生成的插画素材。肌群介绍参考 [OpenStax Anatomy and Physiology 2e](https://openstax.org/details/books/anatomy-and-physiology-2e)；插画用于身体探索，不作为临床解剖图或实测肌肉激活数据。

## 开发、构建与目录

原生 Kotlin + Jetpack Compose，最低 API 26，编译 API 37。使用 Android Studio 打开项目；命令行构建与检查：

```bash
./src/scripts/build-debug.sh --offline
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --offline
```

首次构建需安装相应 Android SDK 并获取依赖，再使用离线模式。Debug APK 输出到 `tmp/build/app/outputs/apk/debug/app-debug.apk`。

- `src/main/`：Kotlin 源码、Manifest、资源与插画。
- `src/test/`、`src/androidTest/`：单元测试与设备端检查。
- `src/scripts/`：构建与发布脚本；`build-release.sh` 生成签名 APK 与校验文件。
- `app/`、`gradle/`：模块配置与 Gradle Wrapper。
- `artwork/readme/`：宣传封面与界面预览。

发布签名通过本机环境配置，私钥和密码不入库。设备检查使用专用 `YangJiBao_API37` 模拟器；最近验证在 API 37 完成，API 26 尚未进行本轮设备复验。
