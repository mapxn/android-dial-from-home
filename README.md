# android-dial-from-home

在**任意桌面**上按数字键、`*`、`#`，直接打开拨号器并预填该按键——和功能机的原厂桌面一样。

Press `0-9`, `*` or `#` on the home screen of **any launcher** and the dialer opens with that key pre-filled, like the stock launcher of a feature phone (LSPosed module).

背景：带实体数字键盘的手机换成第三方桌面（Nova 等）后，按数字键会弹出桌面自己的搜索框和输入法。原厂桌面则是启动 `ACTION_DIAL` + `tel:`。这个模块在系统框架里复现同样的行为，不依赖具体桌面。

在 **百合 BIHEE A89**（Android 13）+ Nova Launcher 上测试过。没有用到任何厂商私有接口，只 hook 了 AOSP 的 `PhoneWindowManager.interceptKeyBeforeDispatching`，理论上通用。

## 效果

- 顶层应用是**默认桌面**，且未锁屏时，按 `0-9` / `*` / `#` → 打开拨号器并预填该字符，这次按键不再交给桌面。
- 在其他应用里按数字键、锁屏页按数字键：**不拦截**。

## 安装

需要：已 root，LSPosed（Zygisk 版）。

可以直接从 [Releases](../../releases) 下载预构建的 `dial-from-home.apk`（使用本地临时生成的调试签名，不是应用商店签名），或自己构建：

```sh
./build.sh      # 需要 JDK 11+ 和 Android SDK，输出 build/dial-from-home.apk
adb install --no-incremental -r build/dial-from-home.apk
```

务必加 `--no-incremental`：增量安装的 APK 在开机时 LSPosed 读不到，模块不会加载。

然后在 LSPosed 管理器里启用「A89 桌面数字键拨号」，**作用域只勾「系统框架」**，重启。

## 实现与限制

- 判断「在桌面上」：取最上层任务的包名，和系统默认桌面（`resolveActivity`，`CATEGORY_HOME`）比较，一致才触发，所以换桌面后自动适配，前提是它被设成**默认桌面**。
- 桌面自己的搜索框里也会被拦截：Nova 的应用抽屉搜索里输入数字会变成打开拨号器。
- 只在 Nova 和原厂桌面上测过，其他桌面是按代码推断的。
- 用 `getRunningTasks`（已弃用，但在系统进程里仍可用）判断顶层应用。

## 许可

MIT，见 [LICENSE](LICENSE)。
