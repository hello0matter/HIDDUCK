# HID Duck

Pixel / Magisk 上的 Duck 脚本点按执行器：一个输入框 + 执行按钮。

## 组成

- Android 应用 `com.local.hidtap`（本仓库根目录）
- Magisk 模块 `magisk-hid-tap`（提供 `/data/adb/modules/hid-tap/hid-ctl.sh`）

应用本身只负责输入 Duck 脚本并调用模块执行。HID 按键由模块直接写 `/dev/hidg0`。

## 构建 App

需要 JDK 17 和 Android SDK 34。

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.5
gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

1. 先装 Magisk 模块 `magisk-hid-tap`（刷入 zip 或把模块目录放到 `/data/adb/modules/hid-tap`）
2. 安装 App
3. 第一次点执行时允许 root
4. USB 接到电脑，先点记事本，再点执行

## Duck 语法（当前支持）

- `REM` 注释
- `DELAY n` 命令之间停顿，单位毫秒
- `DEFAULT_DELAY n` 每个按键间隔，单位毫秒
- `STRING` / `TEXT`
- `ENTER` `TAB` `ESC` `SPACE`
- `GUI` / `CTRL` / `ALT` / `SHIFT` + 键名

App 上的「按键间隔 ms」默认 30，对应每个 HID report 的按下/抬起间隔。DELAY 仍然是命令之间的停顿。

Duck 脚本和间隔会保存在应用本地，下次打开还在。

## 说明

- Pixel 6 上 `/dev/hidg0` 已存在时不要再切 USB 组合，直接执行即可
- 三星 USB HID 仍可能和系统 USB HAL 冲突，不在本仓库范围内

