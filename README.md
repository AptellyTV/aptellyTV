<div align="center">

# Aptelly

### Smart App Matching for TV

**The right apps for your TV.**

**为你的电视匹配全球最热门的应用**

[English](#english) · [简体中文](#简体中文) · [Source](app/src/main) · [Download](https://github.com/AptellyTV/aptellyTV/releases/latest)

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

### Open-source license

The Aptelly Android client is released under the **Apache License 2.0**. Third-party
components remain subject to their respective licenses. The license does not grant
rights to the Aptelly name, logo, or trademarks. See [LICENSE](LICENSE),
[third-party notices](NOTICE.md), and [trademark terms](TRADEMARKS.md).

### Build from source

The repository contains the complete Android client source. Install JDK 21 and the
Android SDK, then run:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The build downloads the pinned official Clash Meta APK and verifies its SHA-256
before packaging. Cloud services, production data, signing keys, and operational
systems are not part of this client repository. See [release policy](RELEASING.md).

Third-party services, subscriptions, accounts, region availability, DRM, and
playback rights remain controlled by their respective providers. Aptelly does not
provide service accounts, viewing rights, or VPN configurations.

---

<a id="简体中文"></a>

## 简体中文

### 关于 Aptelly（适视）

Aptelly（适视）是面向 Android TV 的电视助手，它能帮您找到与您电视相适配的全球热门电视应用。

### 项目能力

- 汇集全球及中国主流电视服务、TV 应用商店、媒体工具、浏览器和网络工具。
- 明确显示应用适合当前电视、当前不可用，或仍在等待已验证的 TV 版本。
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

### 开源协议

Aptelly Android 客户端采用 **Apache License 2.0** 开源协议发布。第三方组件继续适用其
各自的许可证；该协议不授予 Aptelly 名称、Logo 或商标的使用权。详见 [LICENSE](LICENSE)、
[第三方组件声明](NOTICE.md)和[商标条款](TRADEMARKS.md)。

### 从源码构建

本仓库包含完整 Android 客户端源码。安装 JDK 21 和 Android SDK 后运行：

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

构建时会下载固定版本的官方 Clash Meta APK，并在打包前核验 SHA-256。云端服务、生产数据、
签名密钥和运营系统不属于客户端公开仓库。发布规范详见 [RELEASING.md](RELEASING.md)。

第三方服务的账号、订阅、地区可用性、DRM 和播放权益均由对应服务商决定。Aptelly 不提供
第三方账号、观看权益或 VPN 配置。
