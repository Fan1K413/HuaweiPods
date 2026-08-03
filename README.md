<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" alt="HuaweiPods Icon"/>

# HuaweiPods

**让华为耳机接入小米 HyperOS 的系统体验**

[![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-FF6900?style=flat-square)](https://hyperos.mi.com/)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-6F42C1?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![License](https://img.shields.io/github/license/Nshpiter/HuaweiPods?style=flat-square)](LICENSE)

[下载安装](https://github.com/Nshpiter/HuaweiPods/releases) ·
[使用文档](docs/guide/getting-started.md) ·
[问题反馈](https://github.com/Nshpiter/HuaweiPods/issues) ·
QQ群 `1022359908`

**简体中文** · **[English](README_EN.md)**

</div>

HuaweiPods 是一个面向小米 / Redmi HyperOS 设备的 Xposed 模块，将华为耳机接入系统蓝牙详情页、连接弹窗、超级岛与融合设备中心。

> 项目仍在持续适配中。正式版目前支持 **HUAWEI FreeBuds 3** 与 **HUAWEI FreeBuds Pro 3**；下表标记为“测试中”的型号仍须使用对应测试包，请勿跨型号复用控制协议。

## 支持状态

| 型号 | 状态 | 当前能力 |
| --- | --- | --- |
| HUAWEI FreeBuds 3 | 已支持 | 电量、降噪、降噪空间调节、系统界面集成 |
| HUAWEI FreeBuds 5 | 测试中 | 基础识别、电量与降噪协议验证 |
| HUAWEI FreeBuds 6i | 测试中 | 基础识别、电量与降噪协议验证 |
| HUAWEI FreeBuds Pro 3 | 已支持 | 左右耳/充电盒电量、降噪 / 关闭两态控制与状态回读、系统界面集成；暂不支持通透、降噪强度 / 空间调节及手势设置 |
| HUAWEI FreeBuds Pro 4 | 测试中 | 基础识别、电量与降噪协议验证 |
| HUAWEI FreeBuds 7i | 测试中 | 基础识别、电量与降噪协议验证 |
| HUAWEI FreeClip | 测试中 | 基础识别与电量协议验证 |
| HUAWEI FreeClip 2 | 测试中 | 基础识别、电量刷新与双击手势验证（不支持降噪） |
| 华为智能眼镜（第一代） | 测试中 | 左右镜腿电量与系统界面验证 |

需要适配其他华为耳机，可加入 QQ 群 `1022359908` 参与测试与协议采集。

## 主要功能

- 在系统蓝牙详情页显示耳机电量与控制项
- 接入 HyperOS 连接弹窗和超级岛
- 接入融合设备中心，并支持已配对设备间流转
- 显示左耳、右耳和充电盒电量
- 控制主动降噪；FreeBuds 3 支持降噪空间方向调节，FreeBuds Pro 3 支持降噪 / 关闭两态控制

## 使用要求

- 小米或 Redmi 设备
- HyperOS，Android 15 及以上
- LSPosed API 101 及以上
- 正式版需配合 HUAWEI FreeBuds 3 或 HUAWEI FreeBuds Pro 3 使用

## 快速开始

1. 从 [GitHub Releases](https://github.com/Nshpiter/HuaweiPods/releases) 下载并安装 APK。
2. 在 LSPosed 中启用 HuaweiPods。
3. 勾选以下作用域：

   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`

4. 在 HuaweiPods 内重启相关作用域，或重启手机。
5. 连接耳机后，即可在蓝牙详情页、超级岛或融合设备中心查看和控制耳机。

更完整的安装说明见 [快速开始](docs/guide/getting-started.md)。

## 适配新型号

未支持型号需要先采集官方智慧生活 / 智慧音频与耳机之间的真实通信数据。通用协议采集版只负责引导和记录，不代表该型号已经适配。

请勿直接公开包含设备地址、账号或其他个人信息的原始采集文件。提交前请检查并脱敏，完整流程见 [华为耳机协议采集指南](docs/DEBUG_CAPTURE_GUIDE.md)。

建议优先加入 QQ 群 `1022359908` 获取对应型号的测试包；可复现问题则提交至 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues)。

## 构建

```bash
# 正式版
./gradlew :app:assembleRelease

# 协议采集与调试版
./gradlew :app:assembleDebug
```

`release` 不注入华为官方应用；`debug` 包含协议采集功能。两者使用相同应用 ID，无法同时安装。

## 致谢

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen
- [Miuix](https://github.com/YuKongA/miuix)

## 许可证

本项目基于 [GPL-3.0](LICENSE) 开源。
