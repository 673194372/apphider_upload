# 应用列表隐藏器（FPA / LSPatch / LSPosed 模块）

**免 root** 的应用列表隐藏模块，支持导入 **HMA（Hide My Applist）** 导出的配置 JSON。

---

## 为什么不用 HMA

HMA 3.8 起要求「框架必须能在系统服务中加载原生库」，而 FPA / LSPatch / ShizuPosed 都做不到，所以 HMA 在免 root 环境必然显示「模块未激活 / 系统服务未运行」。

本模块只做**客户端 hook** —— 拦截目标 App 进程内的 `PackageManager` 调用。这一层不需要系统服务权限。

---

## 支持导入 HMA 配置

格式完全兼容 HMA 导出的 JSON：

```json
{
  "configVersion": 90,
  "templates": {
    "白名单": {
      "isWhitelist": true,
      "appList": ["com.baidu.searchbox", "com.miHoYo.hkrpg", "..."]
    }
  },
  "scope": { "目标包名": {...} }
}
```

### 两种模式自动识别

| `isWhitelist` | 语义 | 实现 |
|---|---|---|
| `true` | appList 里的是**允许看见**的 | 只保留 appList 内的，其他全部隐藏 |
| `false` | appList 里的是**要隐藏**的 | 只隐藏 appList 内的 |

白名单模式也能完整实现 —— 因为拦的是「返回列表」这个动作，列表到手后按规则过滤即可，不需要预先知道设备装了什么。

### 已实测

用你提供的 `隐藏应用列表白名单配置.json`（2860 项）测试解析器：

```
模板名       : 白名单
isWhitelist  : true
解析出条目数 : 2860          ← 与原文件完全一致
前 5 项      : com.baidu.searchbox, com.baidu.input_mi, ...
```

---

## 拦截范围

| 检测手段 | 状态 |
|---|---|
| `pm.getInstalledPackages()` | ✅ |
| `pm.getInstalledApplications()` | ✅ |
| `pm.getPackageInfo("包名")` | ✅ 抛 NameNotFoundException |
| `pm.getApplicationInfo()` | ✅ |
| `pm.queryIntentActivities()` | ✅ |
| `pm.queryBroadcastReceivers/Services/Providers()` | ✅ |
| `pm.getPackagesForUid()` | ✅ |
| `pm.getInstalledModules()` | ✅ Android 12+ |
| `ActivityManager.getRunningAppProcesses()` | ✅ |
| `ActivityManager.getRunningServices()` | ✅ |
| `ActivityManager.getRunningTasks()` | ✅ |
| `File("/data/data/包名").exists()` | ⚠️ 仅黑名单模式 |
| native 层 `stat()` 直读 | ❌ 免 root 天花板 |
| `pm list packages` shell 命令 | ❌ 走 shell UID |

---

## 构建

### GitHub Actions（推荐）

1. 建仓库，上传本目录所有文件
2. Actions 自动编译，约 3 分钟
3. Artifacts 下载 `AppHider-Module`

workflow 已处理两个坑：

```yaml
# 坑 1：stub 只用于编译，不能打进 dex（否则与框架冲突）
javac -classpath "$ANDROID_JAR:build/stub"   # 编译时引入
d8 $(find build/obj -name "*.class")          # 只打包 obj

# 坑 2：没有 res 目录时 aapt2 compile 会失败
if [ -d src/res ]; then ... else touch build/res.zip; fi
```

---

## 使用

### ① 安装模块

```
装 AppHider.apk 到手机（普通安装，不需要 patch 它自己）
```

### ② 导入配置（可选）

```
打开 AppHider → 点「① 导入 HMA 配置 JSON」
→ 选你的 隐藏应用列表白名单配置.json
→ 显示「导入成功 / 模式: 白名单 / 条目: 2860」
```

导入后会写到：

```
/sdcard/Android/data/com.hider.applist/files/config.json
```

不导入则使用内置默认黑名单（16 项，已包含你设备上的工具）。

### ③ 在 FPA 里 patch 目标 App

```
FPA → 选目标 App
→ Patch 配置勾选 AppHider
→ Hook 核心 LSPPlant，过签 seccomp
→ 开始 Patch
→ 卸载原版 → 装 patched 版
```

### ④ 验证

```powershell
adb logcat | Select-String "AppHider"
```

成功会看到：

```
[AppHider] 载入目标: icu.nullptr.applistdetector
[AppHider/Cfg] 从 /sdcard/.../config.json 加载成功
[AppHider/Cfg] 解析模板 白名单: isWhitelist=true, 条目=2860
[AppHider] 模式: 白名单 | hidden=0 | visible=2860
[AppHider/PM] 安装中 | 模式=白名单 | hidden=0 | visible=2860
[AppHider/PM] getInstalledPackages: 751 → 2860
[AppHider/AM] 进程列表: 1055 → 900
[AppHider] 全部钩子安装完成
```

---

## 配置优先级

```
① 外部 JSON（/sdcard/Android/data/com.hider.applist/files/config.json）
② 内置默认黑名单（Config.DEFAULT_HIDDEN，16 项）
```

外部 JSON 读不到时自动回落到内置默认。

---

## 内置默认黑名单

```java
// 框架类
"com.shizuposed.manager",
"com.tsng.hidemyapplist",
"com.xposed.hider",
"de.robv.android.xposed.installer",
"org.lsposed.manager",
"org.lsposed.lspatch",
"top.nkbe.npatch",
"fun.fpa",

// 工具类
"bin.mt.plus",
"bin.mt.plus.canary",
"bin.mt.termex",
"com.termux",
"org.autojs.autojs6",

// 检测器
"icu.nullptr.applistdetector",
"com.virb3.trustmealready",
"com.junge.justtrustmepp",
```

支持 `"com.foo.*"` 通配。

---

## 局限（必须知道）

### ① Xposed 检测拦不住

如果目标 App 检查：

```java
Class.forName("de.robv.android.xposed.XposedBridge")
// 或扫 /proc/self/maps
```

**这不是「应用列表」，本模块不管。** 而 FPA 注入后这些类本来就在 classloader 里。

不过多数检测器看到 `com.tsng.hidemyapplist` 这个包名就报「Xposed 模块」，那个属于应用列表，本模块能拦。

### ② native 层读取

```c
stat("/data/data/com.tsng.hidemyapplist", &st);
```

Java 的 `File.exists()` hook 对这种无效。

### ③ shell 命令

```bash
pm list packages
```

走 shell UID (2000)，不经过目标 App 进程。

### ④ 白名单模式不支持 File 层

白名单语义是「除了名单全藏」，但文件路径探测不知道对方要查什么包，无法反推。白名单模式下自动跳过 File 层，靠 PM/AM 层已经够用。

---

## 目录结构

```
fpa_apphider/
├── .github/workflows/build.yml   自动构建
├── README.md
├── src/
│   ├── AndroidManifest.xml
│   ├── assets/xposed_init        模块入口声明
│   └── com/hider/applist/
│       ├── HookEntry.java        入口
│       ├── ConfigLoader.java     配置加载 + HMA JSON 解析
│       ├── Config.java           内置默认黑名单
│       ├── PmHook.java           PackageManager 拦截（核心）
│       ├── AmHook.java           ActivityManager 拦截
│       ├── FsHook.java           File 拦截
│       └── MainActivity.java     配置导入界面
└── stub/
    └── de/robv/android/xposed/   Xposed API 编译桩
```

stub 让代码能编译，但**不打进 APK** —— 运行时由 FPA 提供真实的 Xposed API。

---

## 为什么不实现 IXposedHookZygoteInit

```
FPA 会调用 initZygote()
但 StartupParam.startupModulesPath 是空字符串
依赖它读模块资源的代码会崩
```

只 hook `LoadPackage` 是最稳的写法。
