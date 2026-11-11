# TGM3 Android 移植实验

这里同步本次移植中能够单独公开的原创实验、Android 前端和测试。
**这不是可玩测试 APK 的完整源码，不能仅用这个仓库重建该 APK。**
**触控 v2 仅完成部分运行验证：双指组合尚未验证通过，最终沉浸版的 Back 复测也未完成。请先阅读[运行验证状态](docs/port-status.md#touch-frontend-v2-validation-scope)。**

## 目录与可运行范围

- 仓库根目录：可独立构建的 **Port Lab** 诊断 APK。显示合成 GLES2 三角形，运行 JNI 内存测试，不含游戏，也不读取游戏文件。
- `native/`、`include/`、`tests/`：原创 4 KiB 客体页/宿主页分组原型及合成测试。
- `tools/`：只读 PE32 本地文件结构检查和 ELF 对齐检查。
- [`tools/bgm_ogg/`](tools/bgm_ogg/README.md)：原创实验性 BGM OGG/元数据侧车转换工具和合成测试。仅处理自行持有且获授权转换的本地文件，不含音频或匹配游戏加载器；帧数不符会拒绝输出为完成状态。
- `android-shell/`：原创 ZIP 导入器、启动界面、可编辑的半透明触控层及返回退出确认，以及带 zlib 许可的 SDL 2.32.4 Java 接口。**游戏原生库不在仓库中。** 触控说明见 [Touch controls](android-shell/TOUCH-CONTROLS.md)。
- `docs/port-status.md`：私有测试取得的结果、未验证项目和不能复现的具体缺口。
- `docs/source-scope.md`：来源及发布范围。未选择整体项目开源许可证，不将公开可读等同于完整复用授权。

没有游戏资源、游戏 EXE、转译后的游戏代码、无明确许可的 Hri7566/tgm3 运行时或其修改副本，也没有签名密钥和预编译游戏库。

## 运行主机测试

需要 64 位 Linux、GCC 和 Python 3，不需要游戏文件：

```sh
bash run_host_checks.sh
```

覆盖 4 GiB 地址窗口、边界检查、相邻子页保留、重新提交清零、每种页分组 3,000 次确定性随机操作、系统调用失败注入，以及整组释放后的保护故障。
普通版与 ASan/UBSan 版均运行；脚本关闭 LeakSanitizer。PE/ELF 的 17 项测试只使用现场生成的合成字节。

## 构建无游戏诊断 APK

工具链：JDK 17、Gradle 8.11.1、AGP 8.9.2、Android SDK/Build Tools 35、NDK 28.0.13004108、CMake 3.22.1。
先自行安装官方工具链并完成所需许可接受；仓库不包含工具链、Gradle wrapper 或许可接受脚本。

```sh
gradle --no-daemon :app:assembleDebug
# 或生成 x86_64 模拟器版本，注意输出路径相同：
gradle --no-daemon :app:assembleDebug -PprobeAbi=x86_64
```

产物为 `app/build/outputs/apk/debug/app-debug.apk`。完整检查见 [构建说明](app/BUILDING.md)。
仓库不配置自动构建/发布，不会因本次提交触发 Actions。

## 边界

- 16 KiB 分组模拟、ELF/ZIP 对齐和真实 16 KiB Android 内核运行是不同检查。
- 客体页原型的受检访问会检查元数据；同一个宿主页内的裸指针访问没有独立的 4 KiB 硬件隔离。它不是完整 Windows 内存语义实现，也不是私有游戏使用的适配器。
- GL4ES、SDL 原生库、游戏转译和 Win32/JVS 适配不是根目录诊断 APK 的依赖。不要把诊断 PASS 当成游戏兼容性或手机性能证明。
- 不提供游戏下载或分发方法。前端只处理用户自行持有、获授权使用的兼容本地文件。
