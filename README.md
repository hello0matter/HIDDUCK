# HID Duck

Pixel 上用 Duck 脚本点按执行。只要 APK + Magisk root，不再需要 hid-tap 模块。

Pixel / 能用 NetHunter 的机子：在 USB Arsenal 里选 HID 再 Set。本应用不开关 HID。
三星 Z Flip5 的 NetHunter HID 会回弹，不在当前可用范围内。

## 用法

- 音量+ 连按 3 次：显示/隐藏输入框（长期保存）
- 音量- 连按 3 次：激活/禁用空按钮（长期保存）。不开关 HID。
- Hunter 先 Set HID，电脑认出键盘后再点空按钮打字。
- 顶部输入框是每个按键间隔，单位毫秒，填 0 就是尽快打。脚本里的 DELAY 仍是命令之间的停顿。

日志：`/sdcard/hdl.log` 或 `/sdcard/Download/hdl.log`

- `EDT OK/FAIL` 编辑开关
- `ARM OK/FAIL` 空按钮开关
- `HID FAIL` 没有 /dev/hidg0（Hunter 没 Set HID）
- `RUN OK/FAIL` 打字结果
- `CNV FAIL` 脚本转换失败
- `EMP FAIL` 输入为空

## 构建

JDK 17 + Android SDK 34：

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.5
gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

Duck 语法：`REM` `DELAY` `DEFAULT_DELAY` `STRING`/`TEXT` `ENTER` `TAB` `ESC` `SPACE` `GUI`/`CTRL`/`ALT`/`SHIFT`。