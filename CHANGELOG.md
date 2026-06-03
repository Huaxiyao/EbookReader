# 更新日志

## [1.0.0] — 2026-06-02

### ✨ 初始发布

**文境阅读** — 一个原生 Android 全格式电子书阅读器，专为从 Z-Library 下载的电子书设计。

#### 📚 功能
- **书架管理** — 网格展示、格式筛选、搜索、删除
- **导入** — 系统文件选择器 + 文件管理器直接打开
- **阅读器** — 三主题切换（白昼/羊皮纸/夜间）、字号调节（12-32sp）、独立亮度

#### 📖 格式支持
| 格式 | 状态 | 实现方式 |
|------|------|----------|
| ePub | ✅ | ZIP + XML 自解析 → WebView 渲染 |
| PDF | ✅ | AndroidPdfViewer (mhiew fork) |
| MOBI / AZW3 | ✅ | PalmDB 二进制解析 → WebView |
| FB2 | ✅ | XML PullParser 转 HTML |
| CBZ / CBR | ✅ | ZIP/RAR 解压逐页看图 |

#### 🛠 技术栈
- Kotlin + Jetpack Compose + Room + Hilt
- Material 3 设计语言
- GitHub Actions 自动构建 APK
