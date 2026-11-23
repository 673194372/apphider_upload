# 设备环境盾（EnvShield）

**免 root** 的 Android 环境检测拦截模块，适用于 FPA / LSPatch / LSPosed。

比常见的「设备环境盾」多补了 **USB 调试 / 无线调试** 检测拦截。

---

## 拦截范围

| 类别 | 具体检测点 | 状态 |
|---|---|---|
| **开发者选项** | `development_settings_enabled` | ✅ |
| **USB 调试** | `adb_enabled` | ✅ |
| **无线调试** | `adb_wifi_enabled`、`adb_tcp_port` | ✅ **补充** |
| **调试属性** | `ro.debuggable`、`ro.adb.secure`、`init.svc.adbd`、`persist.sys.usb.config` | ✅ **补充** |
| **Root 文件** | `/system/bin/su`、`/data/adb/magisk`、`/sbin/.magisk` 等 31 个路径 | ✅ |
| **su 命令** | `Runtime.exec("su")`、`ProcessBuilder` | ✅ |
| **构建标记** | `Build.TAGS` = test-keys、`Build.DEBUGGABLE` | ✅ |
| **root 应用** | Magisk / SuperSU / KingRoot 等 15 个包名 | ✅ |
| **Hook 痕迹** | `Class.forName("de.robv...")`、StackTrace 里的框架帧 | ✅ |
| **Frida** | 类名与线程名过滤 | ✅ |
| **模拟器** | qemu / goldfish 路径、Build 字段、系统属性 | ✅ |
| **无障碍** | `enabled_accessibility_services` | ✅ |
| **抓包代理** | `http.proxyHost` 等系统属性 | ✅ |
| **蓝牙 / MAC** | `getAddress()`、`getHardwareAddress()` | ✅ |

---

## 重点：USB / 无线调试

原版模块漏了这块。本模块拦截：

### Settings 层

```java
// 需要伪造为 0 的 Global 键
adb_enabled                          // USB 调试总开关
development_settings_enabled         // 开发者选项
adb_wifi_enabled                     // 无线调试（Android 11+）
adb_tcp_port                         // 无线调试端口
wait_for_debugger
debug_app
debug_view_attributes
```

拦截的是 `Settings.Global` / `Settings.Secure` 的 `getInt` / `getLong` / `getString` / `get*ForUser` 全部变体。

### SystemProperties 层

```java
ro.debuggable      → "0"
ro.adb.secure      → "1"
service.adb.root   → "0"
init.svc.adbd      → "stopped"
persist.sys.usb.config → "mtp"
sys.usb.config     → "mtp"
sys.usb.state      → "mtp"
ro.build.type      → "user"
ro.build.tags      → "release-keys"
ro.secure          → "1"
```

---

## 构建

### GitHub Actions（推荐）

1. 建仓库，上传本目录所有文件（**别忘了 `.github` 目录**）
2. Actions 自动编译，约 3 分钟
3. Artifacts 下载 `EnvShield-Module`

> `.github` 是隐藏目录，网页拖拽上传会被忽略。
> 正确做法：用「Create new file」，文件名输入 `.github/workflows/build.yml`，
> 输入 `/` 时 GitHub 会自动建目录。

### 构建时的两个坑

**① stub 不能打进 dex**

```yaml
javac -classpath "$ANDROID_JAR:build/stub"   # 编译时引入
d8 $(find build/obj -name "*.class")          # 只打包 obj
```

否则会和 FPA 注入的真实 Xposed 类冲突，报 `LinkageError`。

**② `xposed_init` 不能有 BOM**

文件必须以 UTF-8 无 BOM 保存，内容只有一行：

```
com.hider.envshield.HookEntry
```

有 BOM 的话 FPA 会把 `\uFEFF` 当成类名的一部分，找不到入口。

**③ `XC_MethodHook` stub 不能是 final**

模块代码要继承它：

```java
public class XC_MethodHook {   // 不能加 final
```

---

## 使用

```
① 装 EnvShield.apk（普通安装，不用 patch 它自己）
② FPA → 选目标 App → 勾选「设备环境盾」
③ Hook 核心 LSPPlant，过签 seccomp
④ Patch → 卸载原版 → 装 patched 版
⑤ 强行停止目标应用后重新打开
```

---

## 配置

所有开关都在 `Config.java`，编译期固定：

```java
boolean hideDevOptions = true;      // 开发者选项 / USB / 无线调试
boolean hideRoot = true;            // Root 检测
boolean hideRootApps = true;        // 隐藏 root 管理应用包名
boolean hideHook = true;            // Hook / Xposed
boolean hideFrida = true;           // Frida
boolean hideEmulator = true;        // 模拟器
boolean hideMultiApp = true;        // 多开
boolean hideAccessibility = true;   // 无障碍
boolean hideProxy = true;           // 代理
boolean hideBluetoothMac = true;    // 蓝牙 / MAC
```

想关掉某项就改 `false`，重新构建。

---

## 验证

```powershell
adb logcat | Select-String "EnvShield"
```

正常输出：

```
[EnvShield] 载入目标: com.yxrj.hulianwang
[EnvShield] 配置: dev=true root=true rootApps=true hook=true frida=true emu=true a11y=true proxy=true bt=true
[EnvShield/Dev] adb_enabled: 1 → 0
[EnvShield/Dev] development_settings_enabled: 1 → 0
[EnvShield/Dev] prop ro.debuggable: 1 → 0
[EnvShield/Root] 文件 exists() /system/bin/su → false
[EnvShield/Pkg] 包名隐藏钩子已安装，共 30 项
[EnvShield/Trace] Class.forName(de.robv.android.xposed.XposedBridge) → ClassNotFound
[EnvShield] 全部钩子安装完成 ✅
```

**看到 `adb_enabled: 1 → 0` 就是 USB 调试拦截生效的证据。**

---

## 局限

免 root 方案的天花板，以下拦不住：

| 检测方式 | 原因 |
|---|---|
| native 层直接 `open("/proc/self/maps")` | Java hook 管不到 C 代码 |
| native `stat("/system/bin/su")` | 同上 |
| `pm list packages` shell 命令 | 走 shell UID，不经过目标进程 |
| 读取 `/proc/self/status` 的 TracerPid | 需要 native hook |
| 服务端下发检测规则 | 本地无法预知 |

**你的目标 App（`com.yxrj.hulianwang` 互连网）如果只做 Java 层检测，本模块能覆盖。**

---

## 目录结构

```
fpa_envshield/
├── .github/workflows/build.yml
├── README.md
├── src/
│   ├── AndroidManifest.xml
│   ├── assets/xposed_init          ← UTF-8 无 BOM，一行
│   └── com/hider/envshield/
│       ├── HookEntry.java          入口
│       ├── Config.java             开关
│       ├── DevOptHook.java         开发者选项/USB/无线调试 ★重点
│       ├── RootHook.java           Root 检测
│       ├── PkgHook.java            root 应用包名隐藏
│       ├── HookTraceHook.java      Hook/Frida 痕迹
│       ├── EnvHook.java            模拟器/无障碍/代理/蓝牙
│       └── MainActivity.java       说明界面
└── stub/
    └── de/robv/android/xposed/     Xposed API 编译桩
```

---

## 为什么不实现 IXposedHookZygoteInit

```
FPA 会调用 initZygote()
但 StartupParam.startupModulesPath 是空字符串
依赖它读模块资源的代码会崩
```

只 hook `LoadPackage` 是最稳的写法。
