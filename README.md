# HID Duck

Pixel 上用 Duck 脚本点按执行。只要 APK + Magisk root，不再需要 hid-tap 模块。

## 用法

- 音量+ 连按 3 次：显示/隐藏输入框（长期保存）
- 音量- 连按 3 次：激活空按钮并打开 HID；再按 3 次禁用空按钮并关掉 HID
- 电脑出现键盘后再点空按钮打字。点执行不会开关 HID。

日志：`/sdcard/hdl.log` 或 `/sdcard/Download/hdl.log`

- `EDT OK/FAIL` 编辑开关
- `ARM OK/FAIL` 空按钮开关
- `HID OK/FAIL` USB 键盘开关
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

三星 USB HID 仍可能和系统 USB HAL 冲突，不在本仓库范围内。