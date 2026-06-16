# karaokeClient

Android TV 播放端，适配新版 KTV 后端。

[![Build APK](https://github.com/nazo-x1/karaokeClient/actions/workflows/build-apk.yml/badge.svg)](https://github.com/nazo-x1/karaokeClient/actions/workflows/build-apk.yml)

## 功能

- 通过 `GET /api/v1/playback/songs/{id}` 获取 plain / enhanced 播放模式
- 媒体流：`GET /api/v1/playback/stream/{id}/video|vocals|accompaniment`
- **plain**：单视频带原声播放
- **enhanced**：静音视频 + 原唱/伴奏双音轨（覆写或内嵌 MKV 均由服务端解析）
- 队列状态：`GET /api/v1/queue` 返回 `state` 字段（`pending` / `playing` / `sung`）
- 未就绪歌曲：轮询 `prepare-status` + `ensure-ready`，SSE `PREPARE_READY`(9) 触发重载
- `QUEUE_CHANGED`(8)：刷新队列；空闲时自动预加载队首（含 prepare 等待）

## 使用

1. 安装到 Android TV
2. **首次启动**：无已保存地址时进入「首次连接」屏，输入 `http://192.168.x.x:15233` 并连接
3. **已保存地址**：自动连接 SSE、拉取队列
4. 按 **MENU** 打开侧边栏点歌；手机/Web RC 点歌后 TV 自动播放

仅支持 Android TV（Compose for TV + Leanback Launcher）。

## 架构（v2）

```
com.example.karaoke/
├── KaraokeApp.kt          # Compose 根 + KaraokeTheme
├── MainActivity.kt        # ComponentActivity + 按键分发
├── di/AppContainer.kt
├── data/                  # API、SSE、Repository、Prefs
├── playback/PlaybackEngine.kt
└── ui/
    ├── components/        # 统一 TV 组件（Text/Button/Card/Toast…）
    ├── theme/             # KaraokeTheme + DesignTokens + 1920×1080 自适应缩放
    ├── setup/ player/ drawer/
    └── UiMessenger.kt     # 应用内消息（替代 Toast）
```

## CI/CD

推送 `main` 或发起 PR 时，GitHub Actions 自动执行 `./gradlew assembleDebug` 并上传 APK 产物（保留 90 天）。

- 在 [Actions](https://github.com/nazo-x1/karaokeClient/actions/workflows/build-apk.yml) 页面下载 `karaoke-apk-*` artifact
- 也可在 Actions 页手动触发 **Run workflow**
- 推送 `v*` 标签（如 `v1.0.1`）时，自动创建 GitHub Release 并附带 APK 与 sha256 校验文件

本地构建：

```bash
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```
