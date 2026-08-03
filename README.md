<div align="center">

# Aptelly

### Smart App Matching for TV

**The right apps for your TV.**

**适视 · 为你的电视，匹配真正合适的应用**

[English](#english) · [简体中文](#简体中文) · [Download](https://github.com/AptellyTV/aptellyTV/releases/latest)

</div>

---

<a id="english"></a>

## English

### About

Aptelly is a TV-first app discovery, installation, and management assistant for
Android TV. It helps each television find apps that fit its device capabilities,
system version, region, and remote-control experience.

### Capabilities

- Discovers popular global and Chinese TV services, TV app stores, media tools,
  browsers, and network utilities.
- Identifies whether an app is suitable, unavailable, or still awaiting a verified
  TV build for the current device.
- Selects a usable TV installation path and never presents a web page as a completed
  app installation.
- Verifies APK package name, publisher certificate, version, SHA-256, Android API,
  CPU architecture, and TV launcher support before or after installation.
- Installs, opens, updates, and removes TV apps from one remote-friendly interface.
- Includes the official universal Clash Meta build as an offline network bootstrap;
  users supply and control their own network configuration.
- Provides a clean TV home experience with no sponsored tiles or startup ads.
- Supports signed Aptelly updates and Android's final installation confirmation.
- Keeps optional compatibility diagnostics off by default and lets users delete
  records linked to their installation.

### Download

- Requires Android TV 8.0 (API 26) or later.
- [Download the latest official release](https://github.com/AptellyTV/aptellyTV/releases/latest).
- Verify the APK against the `.sha256` file included in the same release.
- The universal APK includes Clash Meta for ARMv7, ARM64, x86, and x86_64, so the
  download is larger than a single-architecture build.
- Only the five most recent official releases are retained.

### Project model

Aptelly follows **Open Client, Managed Matching Platform**: the auditable Android
client is being prepared for Apache-2.0 publication, while production matching,
compatibility data, operational safeguards, signing keys, and production
configuration remain privately managed. This repository is currently the official
binary release channel.

Third-party services, subscriptions, accounts, region availability, DRM, and
playback rights remain controlled by their respective providers. Aptelly does not
provide service accounts, viewing rights, or VPN configurations.

---

<a id="简体中文"></a>

## 简体中文

### 关于 Aptelly（适视）

Aptelly（适视）是面向 Android TV 的电视应用发现、安装与管理助手。它帮助每台电视根据
设备能力、系统版本、所在地区和遥控器体验，找到真正适合的应用。

### 项目能力

- 汇集全球及中国主流电视服务、TV 应用商店、媒体工具、浏览器和网络工具。
- 明确显示应用适合当前电视、当前不可用，或仍在等待已验证的 TV 版本。
- 为当前电视选择可用安装路径，不把网页跳转伪装成已经完成的应用安装。
- 在安装前后核验 APK 包名、发布者证书、版本、SHA-256、Android API、CPU 架构及
  电视启动入口。
- 在适配遥控器的统一界面中安装、打开、更新和删除电视应用。
- 离线内置官方 Clash Meta 通用版，作为基础网络工具；网络配置完全由用户自行提供和管理。
- 提供纯净电视主页，不显示推广卡片或启动广告。
- 支持 Aptelly 签名更新，并始终由 Android 提供最终安装确认。
- 可选兼容性诊断默认关闭，用户可以删除与本次安装关联的记录。

### 下载

- 需要 Android TV 8.0（API 26）或更高版本。
- [下载最新正式版](https://github.com/AptellyTV/aptellyTV/releases/latest)。
- 安装前请使用同一 Release 中的 `.sha256` 文件核对 APK。
- 通用 APK 内含适配 ARMv7、ARM64、x86 和 x86_64 的 Clash Meta，因此体积大于单一
  架构安装包。
- 发布仓库只保留最近 5 个正式版本。

### 项目模式

Aptelly 采用 **客户端开放、匹配平台托管运营** 模式：可审计 Android 客户端正在准备以
Apache-2.0 许可证发布；生产匹配服务、兼容性数据、运营安全措施、签名密钥和生产配置保持
私有。本仓库当前仅作为官方二进制发布渠道。

第三方服务的账号、订阅、地区可用性、DRM 和播放权益均由对应服务商决定。Aptelly 不提供
第三方账号、观看权益或 VPN 配置。
