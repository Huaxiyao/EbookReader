# 📖 文境阅读 — Android 全格式电子书阅读器

<p align="center">
  <img src="https://github.com/Huaxiyao/EbookReader/actions/workflows/build.yml/badge.svg" alt="Build">
  <img src="https://github.com/Huaxiyao/EbookReader/actions/workflows/release.yml/badge.svg" alt="Release">
  <img src="https://img.shields.io/badge/Android-34-green" alt="Android">
  <img src="https://img.shields.io/badge/Kotlin-1.9-blue" alt="Kotlin">
  <img src="https://img.shields.io/badge/License-MIT-orange" alt="License">
</p>

<p align="center">
  <b>文境阅读</b> 是一个原生 Android 电子书阅读器，专为从 Z-Library 下载的电子书设计。<br>
  一键导入、多格式阅读、个性化主题，让阅读回归纯粹。
</p>

---

## 📱 截图

> 待补充 — 构建成功后截图替换此处

```
[书架页面截图]    [阅读器截图 - 白昼]    [阅读器截图 - 夜间]    [设置面板截图]
```

---

## 📥 下载

| 渠道 | 说明 | 链接 |
|------|------|------|
| 🏗️ **最新开发版** | 每次 `git push` 自动构建 | [Actions](https://github.com/Huaxiyao/EbookReader/actions/workflows/build.yml) → Artifacts |
| 🚀 **稳定版** | 打 tag 发版时构建 | [Releases](https://github.com/Huaxiyao/EbookReader/releases) |

**快速下载：**
1. 打开 [Actions 页面](https://github.com/Huaxiyao/EbookReader/actions/workflows/build.yml)
2. 点击最新的绿色 ✓ 构建
3. 底部 **Artifacts** → 下载 `EbookReader-Debug.zip`
4. 解压 → `app-debug.apk` → 传到手机安装

---

## ✨ 功能一览

### 📚 书架
| 功能 | 说明 |
|------|------|
| 网格展示 | 3 列网格，封面 + 书名 + 作者 + 进度条 |
| 格式筛选 | 按 ePub / PDF / MOBI 等筛选 |
| 搜索 | 按书名/作者搜索 |
| 一键导入 | 系统文件选择器，支持多选 |

### 📖 阅读器
| 功能 | 说明 |
|------|------|
| **三主题** | 白昼 · 羊皮纸 · 夜间，一键切换 |
| **字号调节** | 12sp–32sp 滑动调节 |
| **亮度覆盖** | 独立于系统的亮度滑块 |
| **进度保存** | 自动保存 + 恢复进度 |
| **工具栏** | 点击屏幕中央唤出，3 秒自动隐藏 |

### 📄 格式支持

| 格式 | 解析方式 | 渲染 |
|------|----------|------|
| **ePub** | ZIP + XML 自解析（无第三方库依赖） | WebView + CSS 主题 |
| **PDF** | AndroidPdfViewer (mhiew fork) | 原生翻页 + 缩放 |
| **MOBI / AZW3** | PalmDB 二进制解析 | WebView |
| **FB2** | XmlPullParser 转 HTML | WebView |
| **CBZ** (漫画) | ZIP 解压逐页显示 | Compose Image |
| **CBR** (漫画) | RAR 解压 (junrar) | Compose Image |

---

## 🏗️ 架构

```
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                         │
│              (Navigation Compose)                        │
├────────────────────┬────────────────────────────────────┤
│    LibraryScreen    │          ReaderScreen               │
│   (书架 · Compose)   │     (阅读器 · Compose + WebView)     │
├────────┬───────────┴──────────────────┬─────────────────┤
│LibraryVM│    ReaderViewModel           │ ReaderSettings  │
│         │      (状态管理)               │  (设置面板)       │
├─────────┴──────────┬───────────────────┴─────────────────┤
│   FileImporter      │   EpubRenderer · PdfRenderer        │
│   (文件导入)         │   MobiRenderer · Fb2Renderer        │
│                     │   ComicRenderer                     │
├─────────────────────┴───────────────────────────────────-┤
│                    BookRepository                         │
│                      (数据层)                              │
├──────────────────────┬──────────────────────────────────-┤
│     Room Database     │         File System                │
│   (书籍元数据 + 进度)   │    (电子书文件 + 封面缓存)           │
└──────────────────────┴──────────────────────────────────-┘
```

### 模块说明

```
app/src/main/java/com/ebookreader/
├── MainActivity.kt           # 导航入口
├── EbookApplication.kt       # Hilt 应用
├── library/                  # 书架模块
│   ├── LibraryScreen.kt      # 书架 UI
│   └── LibraryViewModel.kt   # 书架逻辑
├── reader/                   # 阅读器模块
│   ├── ReaderScreen.kt       # 阅读器主界面
│   ├── ReaderViewModel.kt    # 阅读器逻辑
│   ├── renderer/             # 5 种格式渲染器
│   │   ├── EpubRenderer.kt
│   │   ├── PdfRenderer.kt
│   │   ├── MobiRenderer.kt
│   │   ├── Fb2Renderer.kt
│   │   └── ComicRenderer.kt
│   └── settings/
│       └── ReaderSettingsSheet.kt
├── imports/                  # 导入模块
│   └── FileImporter.kt
├── data/                     # 数据层
│   ├── model/Book.kt
│   ├── db/ (Room)
│   └── repository/
├── di/                       # 依赖注入
└── ui/                       # 主题 / 通用组件
```

---

## 🛠️ 技术栈

| 类别 | 选用方案 |
|------|----------|
| 语言 | **Kotlin** |
| UI | **Jetpack Compose** + Material 3 |
| 数据库 | **Room** (SQLite) |
| DI | **Hilt** |
| 导航 | Navigation Compose |
| PDF | AndroidPdfViewer (mhiew fork) |
| RAR | junrar |
| 图片 | Coil |
| 渲染 | WebView (ePub/MOBI/FB2) |
| 构建 | Gradle 8.5 + AGP 8.2.2 |
| CI/CD | GitHub Actions |

---

## 🚀 构建

### 本地构建

```bash
# 克隆
git clone https://github.com/Huaxiyao/EbookReader.git
cd EbookReader

# Debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK（需要签名配置）
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

> 💡 需要先装 [Android Studio](https://developer.android.com/studio)，首次打开会自动下载 SDK。

### 发布新版本

```bash
# 打 tag 推送到 GitHub
git tag v1.1.0
git push origin v1.1.0
```

GitHub Actions 自动构建 Release APK 并创建 Release 页面。

### 配置正式签名

在 GitHub 仓库 **Settings → Secrets and variables → Actions** 中添加：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | 签名密钥文件的 base64 编码 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

---

## 📖 使用流程

1. 在 [Z-Library](https://z-lib.io) 下载 `.epub` / `.pdf` / `.mobi` 等电子书
2. 打开「文境阅读」，点击右下角 **+** 按钮
3. 在系统文件选择器中选中下载的文件（支持多选）
4. 书籍自动出现在书架，点击封面开始阅读
5. 阅读时点击屏幕中央唤出工具栏和设置面板

---

## 📋 路线图

- [x] 书架管理（导入 / 筛选 / 搜索 / 删除）
- [x] ePub + PDF 阅读
- [x] MOBI / AZW3 / FB2 阅读
- [x] CBZ / CBR 漫画阅读
- [x] 三主题切换
- [x] 字号 / 亮度调节
- [x] 阅读进度自动保存
- [ ] 云端同步（进度跨设备）
- [ ] 划线 / 笔记
- [ ] TTS 朗读
- [ ] WiFi 传书
- [ ] Calibre 服务器导入
- [ ] 暗色图标适配

---

## 📄 开源许可

本项目基于 [MIT License](LICENSE) 开源。

---

<p align="center">
  <b>文境阅读</b> · 为阅读而设计<br>
  Made with ❤️
</p>
