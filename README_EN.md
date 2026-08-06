<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HuaweiPods Icon"/>

# HuaweiPods

**Huawei audio device integration for Xiaomi HyperOS**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-orange?style=flat-square)](https://hyperos.mi.com)

**English** | **[Simplified Chinese](README.md)**

</div>

HuaweiPods is an Xposed module for Xiaomi HyperOS. It integrates supported Huawei audio devices with the system headset popup, Super Island, Fusion Device Center, and Bluetooth detail page.

Starting with 1.2.0, the 11 models below are included in one release APK. Inclusion in the unified build does not mean that every feature has completed device testing.

## Support status

| Model | Status | Integrated capabilities |
| --- | --- | --- |
| HUAWEI FreeBuds 3 | Stable | Battery, ANC on/off, nine-position spatial ANC dial, double-tap gestures, and system UI integration |
| HUAWEI FreeBuds 5 | Basic support needs retesting | Battery and ANC/off; no verified ANC state readback or gesture settings |
| HUAWEI FreeBuds 6i | Extended features need retesting | Battery, transparency/ANC/off, four ANC levels, voice transparency, double/triple-tap gestures, and dedicated images |
| HUAWEI FreeBuds Pro 3 | Extended features need retesting | Battery, three-mode control and readback, four ANC levels, voice transparency, long-press/pinch/swipe gestures |
| HUAWEI FreeBuds Pro 4 | Basic support needs retesting | Battery and ANC/off; no verified ANC state readback or gesture settings |
| HUAWEI FreeBuds Pro 5 | Basic support needs retesting | Battery, transparency/ANC/off, and state readback; ANC levels and gestures remain pending |
| HUAWEI FreeBuds 7i | Basic support needs retesting | Battery and ANC/off; no verified ANC state readback or gesture settings |
| HUAWEI FreeClip | Basic support needs retesting | Left/right/case battery; no traditional ANC |
| HUAWEI FreeClip 2 | Extended features need retesting | Battery, double/triple-tap and swipe gestures, spatial audio, and selected wearing/audio settings; no traditional ANC |
| HUAWEI Eyewear (1st generation) | Basic support needs retesting | Left/right temple battery and system UI integration; no ANC |
| HUAWEI Eyewear 2 | Basic support needs retesting | Left/right temple battery and double-tap/swipe gestures; no ANC |

“Extended features need retesting” means that core behavior has tester feedback but newly added controls still need a full model-specific regression. “Basic support needs retesting” means that identification, battery, or the core protocol is integrated without a complete device test pass.

## Features

- **Battery display** for earbuds, charging cases, or the left/right temples of supported eyewear.
- **Model-aware controls** for ANC, transparency, ANC levels, and gestures where verified protocol data is available.
- **ANC dial** for FreeBuds 3 spatial noise cancellation adjustment only.
- **System Bluetooth detail page** integration for battery and controls supported by the selected model.
- **Super Island / popup** status display and quick ANC controls.
- **Fusion Device Center** headset display and transfer between paired devices.
- **Manual model binding** by Bluetooth address when a device has been renamed or cannot be identified automatically.

## Requirements

- Xiaomi / Redmi device running HyperOS.
- Android 15+.
- LSPosed API version >= 101.
- A paired device listed in the support table above.

## Usage

1. Install the HuaweiPods APK.
2. Enable the module in LSPosed.
3. Select the recommended scopes:
   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`
4. Reboot the phone, or restart the scoped apps from HuaweiPods.
5. Connect a supported device and view its integrated capabilities in HuaweiPods, Super Island, Fusion Device Center, or the system Bluetooth detail page. If a renamed device is not identified, select its actual model manually in HuaweiPods.

For model-specific retesting and protocol capture, join QQ group `1022359908`.

## Development Notes

Internal package names, broadcast actions, configuration names, and the public app identity are unified as HuaweiPods.

## Credits

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z — the fork HuaweiPods was directly adapted from.
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky — the original upstream OppoPods project.
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — original HyperOS headset integration ideas.
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS-style Compose UI components.

## License

[GPL-3.0](LICENSE)
