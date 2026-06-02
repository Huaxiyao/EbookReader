# 文境阅读 — Android 全格式电子书阅读器

![Build](https://github.com/Huaxiyao/EbookReader/actions/workflows/build.yml/badge.svg)
![Release](https://github.com/Huaxiyao/EbookReader/actions/workflows/release.yml/badge.svg)
![Android](https://img.shields.io/badge/Android-34-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)
![License](https://img.shields.io/badge/License-MIT-orange)

一个原生的 Android 电子书阅读器，专为从 Z-Library 下载的电子书设计。支持一键导入、多格式阅读、个性化主题设置。

## 📥 下载 APK

有两种方式拿到安装包：

### ⚡ 直接下载（推荐）

| 渠道 | 说明 | 链接 |
|------|------|------|
| 🏗️ **最新开发版** | 每次代码推送自动构建 | [Actions → 下载](https://github.com/Huaxiyao/EbookReader/actions/workflows/build.yml) |
| 🚀 **稳定版** | 打 tag 发版时构建 | [Releases → 下载](https://github.com/Huaxiyao/EbookReader/releases) |

**快速下载开发版：**
1. 打开 [Actions 页面](https://github.com/Huaxiyao/EbookReader/actions/workflows/build.yml)
2. 点击最新的绿色 ✓ 构建
3. 拉到最下面 **Artifacts** → 下载 `EbookReader-Debug.zip`
4. 解压后得到 `app-debug.apk`，传到手机安装即可

### 🔨 自行构建

```bash
git clone https://github.com/Huaxiyao/EbookReader.git
cd EbookReader
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

## ✨ 功能

| 功能 | 状态 |
|------|------|
| 📚 **书架管理** — 导入、分类、搜索、删除 | ✅ |
| 📖 **ePub 阅读** — CSS 主题、字号调整、滚动进度 | ✅ |
| 📄 **PDF 阅读** — 翻页、缩放、滚动 | ✅ |
| 📘 **MOBI/AZW3 阅读** — 自动解析并渲染 | ✅ |
| 📗 **FB2 阅读** — XML 解析、章节导航 | ✅ |
| 🎨 **CBZ/CBR 漫画** — 图片解压、逐页阅读 | ✅ |
| 🎭 **三主题切换** — 白昼 / 羊皮纸 / 夜间 | ✅ |
| 🔆 **亮度调节** — 独立亮度覆盖 | ✅ |
| 🔤 **字号调节** — 12sp–32sp 滑动调节 | ✅ |
| 📊 **阅读进度** — 自动保存、进度条显示 | ✅ |
| 📂 **文件关联** — 从文件管理器直接打开电子书 | ✅ |

## 📁 项目结构

```
EbookReader/
├── app/src/main/java/com/ebookreader/
│   ├── MainActivity.kt           # 入口 + 导航
│   ├── EbookApplication.kt       # Application
│   ├── library/                  # 书架模块
│   │   ├── LibraryScreen.kt      # 书架 UI (Jetpack Compose)
│   │   └── LibraryViewModel.kt   # 书架逻辑
│   ├── reader/                   # 阅读器模块
│   │   ├── ReaderScreen.kt       # 阅读器主界面
│   │   ├── ReaderViewModel.kt    # 阅读器逻辑
│   │   ├── renderer/             # 格式渲染器
│   │   │   ├── EpubRenderer.kt   # ePub → WebView
│   │   │   ├── PdfRenderer.kt    # PDF → PDFView
│   │   │   ├── MobiRenderer.kt   # MOBI/AZW3 解析
│   │   │   ├── Fb2Renderer.kt    # FB2 (XML) 解析
│   │   │   └── ComicRenderer.kt  # CBZ/CBR 图片解压
│   │   └── settings/
│   │       └── ReaderSettingsSheet.kt  # 设置面板
│   ├── imports/                  # 导入模块
│   │   └── FileImporter.kt       # 文件导入服务
│   ├── data/                     # 数据层
│   │   ├── model/Book.kt         # 书籍实体
│   │   ├── db/                   # Room 数据库
│   │   └── repository/           # 仓库
│   ├── di/                       # 依赖注入
│   └── ui/                       # UI 主题/组件
│       ├── theme/Theme.kt
│       └── components/
└── app/build.gradle.kts          # 依赖配置
```

## 🛠 技术栈

- **Kotlin** — 语言
- **Jetpack Compose** — 声明式 UI
- **Room** — 本地数据库
- **Hilt** — 依赖注入
- **Navigation Compose** — 页面导航
- **AndroidPdfViewer** — PDF 渲染
- **epublib** — ePub 解析
- **junrar** — RAR 解压
- **Coil** — 图片加载
- **WebView** — HTML 内容渲染

## 🚀 构建

### Debug APK（开发测试）

```bash
git clone https://github.com/Huaxiyao/EbookReader.git
cd EbookReader
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

### Release APK（发布新版本）

```bash
# 1. 打 tag
git tag v1.0.0
git push origin v1.0.0

# 2. GitHub Actions 会自动：
#    - 构建 Release APK
#    - 签名（如果配置了签名密钥）
#    - 创建 GitHub Release 页面
#    - 上传 APK 到 Release 附件
```

> 💡 如果想用正式签名（而不是 debug 签名），在 GitHub 仓库设置中添加以下 Secrets：
> - `KEYSTORE_BASE64` — 签名密钥文件的 base64 编码
> - `KEYSTORE_PASSWORD` — 密钥库密码
> - `KEY_ALIAS` — 密钥别名
> - `KEY_PASSWORD` — 密钥密码

## 📥 使用流程

1. 在 Z-Library 下载 `.epub` / `.pdf` / `.mobi` 等格式的电子书
2. 打开「文境阅读」，点击右下角 **+** 按钮
3. 在文件选择器中找到并选中下载的电子书
4. 书籍自动出现在书架中，点击即可开始阅读
5. 阅读时点击屏幕中央可唤出工具栏和设置面板

## 📋 待办 / 可扩展

- [ ] 云端同步（阅读进度跨设备）
- [ ] 笔记/划线功能
- [ ] TTS 朗读
- [ ] 书籍元信息编辑
- [ ] 导入 .zip 格式
- [ ] 自动从 Calibre 服务器导入
