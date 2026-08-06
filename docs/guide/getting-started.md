---
title: 快速开始
description: 在小米 HyperOS 上安装、启用并检查 HuaweiPods。
---

# 快速开始

HuaweiPods 是面向小米 HyperOS 的 Xposed 模块。从 1.2.0 起，支持表中的 11 个型号使用同一个正式 APK；各型号的实机验证程度和可用控制并不相同，请先查看[支持状态](../support/index.md)。

::: warning 安装前确认
HuaweiPods 需要正常工作的 LSPosed 环境，并会修改系统蓝牙相关进程的行为。请先确认你了解 Xposed 模块的启用、停用与恢复方式。
:::

## 环境要求

- 小米或 Redmi 设备，运行 HyperOS；
- Android 15 或更高版本；
- LSPosed API 版本 101 或更高；
- 已在系统蓝牙中配对[支持列表](../support/index.md)中的设备。

## 1. 安装 HuaweiPods

从 [GitHub Releases](https://github.com/Nshpiter/HuaweiPods/releases) 下载正式 APK，正常安装后打开 HuaweiPods。1.2.0 及以上版本无需寻找机型专用包。

## 2. 启用 LSPosed 作用域

在 LSPosed 中启用 HuaweiPods，并勾选以下作用域：

```text
com.android.bluetooth
com.android.settings
com.milink.service
com.xiaomi.bluetooth
```

## 3. 重启并连接耳机

启用后可先在 HuaweiPods 中重启相关作用域；若系统组件没有重新加载模块，再完整重启手机。之后连接已配对的受支持设备。

模块会按官方名称自动识别型号。如果耳机被改名或未自动识别，请进入 HuaweiPods 的设备选择页，按蓝牙地址为它选择真实型号。手动绑定只影响该设备，不会把同名设备的控制协议混用。

你可以依次检查：

1. HuaweiPods 首页是否显示模块已激活；
2. 左右耳与充电盒电量，或眼镜左右镜腿电量是否更新；
3. 系统蓝牙详情页是否出现该型号支持的状态与控制；
4. 重新连接耳机后，超级岛或系统弹窗是否出现；
5. 融合设备中心是否显示耳机。

FreeClip、FreeClip 2 和两代 Eyewear 不提供传统主动降噪，看不到降噪入口是正常现象。标记为“待复测”的功能如果与官方 App 表现不一致，请保留复现步骤并反馈。

## 没有生效时

按下面顺序排查，通常不需要反复卸载：

1. 确认 LSPosed 中 HuaweiPods 已启用，且 API 版本满足要求；
2. 核对四个作用域是否全部勾选；
3. 在 HuaweiPods 内重启相关作用域；仍无效时再重启手机；
4. 在系统蓝牙中断开再连接耳机；
5. 在设备选择页确认当前蓝牙地址绑定的是实际型号；
6. 对照[支持状态](../support/index.md)，确认该入口确实属于当前机型。

仍无法复现时，可以到 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues) 提交耳机型号、手机型号、HyperOS 版本、LSPosed 版本、HuaweiPods 版本和复现步骤，也可以加入 QQ 群 `1022359908` 参与复测。

## 更新或卸载

- 同签名的新版本可以直接覆盖安装；
- 如果系统提示签名不一致，请改用同一发布渠道提供的版本；
- 停用或卸载前，先在 LSPosed 中取消 HuaweiPods 作用域，再重启相关进程或手机。
