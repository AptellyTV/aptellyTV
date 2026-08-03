# AptellyTV Releases

这里仅提供 Aptelly 正式签名 APK、SHA-256 校验文件和版本说明；完整源码保存在私有仓库。

- [下载最新正式版](https://github.com/gogo56924056/aptellyTV/releases/latest)
- 主 APK 保留 Clash Meta 通用版，兼容 ARMv7、ARM64、x86 和 x86_64，因此包体约 103 MB。
- 安装前请核对同一 Release 中的 `.sha256` 文件。
- 自动保留最近 5 个正式版本；发布第 6 个版本时删除最早的 Release 及其标签。

生产签名密钥不在 GitHub 中保存。Release 资产由受控开发机完成签名、`apksigner` 验证和
SHA-256 校验后上传。
