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

> 从 1.2.0 起，下列 11 个型号合并到同一个正式包，不再按型号分发 APK。合入统一包不等于全部功能都已完成实机验证，请以表中的状态为准。

## 支持状态

| 型号 | 状态 | 当前能力 |
| --- | --- | --- |
| HUAWEI FreeBuds 3 | 稳定 | 电量、降噪开关、9 档降噪空间方向、双击手势与系统界面集成 |
| HUAWEI FreeBuds 5 | 基础支持待复测 | 电量、降噪 / 关闭两态控制；暂不支持降噪状态回读与手势设置 |
| HUAWEI FreeBuds 6i | 扩展功能待复测 | 电量、通透 / 降噪 / 关闭、4 档降噪、通透人声模式、双击 / 三击手势与专属图片 |
| HUAWEI FreeBuds Pro 3 | 扩展功能待复测 | 电量、三态控制与状态回读、4 档降噪、通透人声模式、长按 / 捏合 / 滑动手势 |
| HUAWEI FreeBuds Pro 4 | 基础支持待复测 | 电量、降噪 / 关闭两态控制；暂不支持降噪状态回读与手势设置 |
| HUAWEI FreeBuds Pro 5 | 基础支持待复测 | 电量、通透 / 降噪 / 关闭与状态回读；降噪等级和手势待补充 |
| HUAWEI FreeBuds 7i | 基础支持待复测 | 电量、降噪 / 关闭两态控制；暂不支持降噪状态回读与手势设置 |
| HUAWEI FreeClip | 基础支持待复测 | 左右耳与充电盒电量；不提供传统主动降噪 |
| HUAWEI FreeClip 2 | 扩展功能待复测 | 电量、双击 / 三击 / 滑动手势、空间音频及部分佩戴和音频设置；不支持传统主动降噪 |
| 华为智能眼镜（第一代） | 基础支持待复测 | 左右镜腿电量与系统界面集成；不提供主动降噪 |
| HUAWEI Eyewear 2 | 基础支持待复测 | 左右镜腿电量、双击 / 滑动手势；不提供主动降噪 |

“扩展功能待复测”表示基础能力已有测试反馈，但本次新增控制仍需对应机型回归；“基础支持待复测”表示识别、电量或核心协议已经接入，尚未完成一轮完整实机验证。

需要适配其他华为耳机，可加入 QQ 群 `1022359908` 参与测试与协议采集。

## 主要功能

- 在系统蓝牙详情页显示电量及机型支持的控制项
- 接入 HyperOS 连接弹窗和超级岛
- 接入融合设备中心，并支持已配对设备间流转
- 显示左右耳、充电盒或眼镜左右镜腿电量
- 按机型提供主动降噪、通透模式、降噪等级和手势设置
- 耳机名称被修改或无法自动识别时，可按蓝牙地址手动选择型号
- 首次启动提供设置引导，并可在应用内检查 GitHub 更新
- 覆盖安装新版本后提示重启作用域，无需直接重启手机

## 使用要求

- 小米或 Redmi 设备
- HyperOS，Android 15 及以上
- LSPosed API 101 及以上
- 表中任一已集成型号

## 快速开始

1. 从 [GitHub Releases](https://github.com/Nshpiter/HuaweiPods/releases) 下载并安装 APK；首次打开可按引导检查 LSPosed 与核心作用域。
2. 在 LSPosed 中启用 HuaweiPods。
3. 勾选以下作用域：

   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`

4. 在 HuaweiPods 内重启相关作用域，或重启手机。
5. 连接设备后，即可在 HuaweiPods、蓝牙详情页、超级岛或融合设备中心查看已接入能力。若改过蓝牙名称而未识别，请在 HuaweiPods 中手动选择真实型号。

更完整的安装说明见 [快速开始](docs/guide/getting-started.md)。

## 适配新型号

未支持型号需要先采集官方智慧生活 / 智慧音频与耳机之间的真实通信数据。通用协议采集版只负责引导和记录，不代表该型号已经适配。

请勿直接公开包含设备地址、账号或其他个人信息的原始采集文件。提交前请检查并脱敏，完整流程见 [华为耳机协议采集指南](docs/DEBUG_CAPTURE_GUIDE.md)。

建议优先加入 QQ 群 `1022359908` 参与对应型号复测；可复现问题则提交至 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues)。

## 构建

```bash
# 正式版
./gradlew :app:assembleRelease

# 协议采集与调试版
./gradlew :app:assembleDebug
```

`release` 不注入华为官方应用；`debug` 包含协议采集功能。两者使用相同应用 ID，无法同时安装。

## 致谢

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z（HuaweiPods 直接基于）
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky（上游原始项目）
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen
- [HyperIsland](https://github.com/1812z/HyperIsland) by 1812z（更新与首次引导交互参考）
- [Miuix](https://github.com/YuKongA/miuix)

## 许可证

本项目基于 [GPL-3.0](LICENSE) 开源。
