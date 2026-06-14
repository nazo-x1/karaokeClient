# karaokeClient

Android TV 播放端，适配新版 KTV 后端。

## 功能

- 通过 `GET /song/{id}/playback` 获取 plain / enhanced 播放模式
- 媒体流：`GET /song/stream/{id}/video|vocals|accompaniment`（不再使用 `/download/`）
- **plain**：单视频带原声播放
- **enhanced**：静音视频 + 原唱/伴奏双音轨（覆写或内嵌 MKV 均由服务端解析）
- SSE 遥控与 Web 控制台同步（切歌、暂停、音量、原唱/伴奏切换）

## 使用

1. 安装到 Android TV
2. 启动后输入服务端地址，如 `http://192.168.1.20:15233`
3. 在手机/Web 控制台点歌，TV 端自动播放

仅支持 Android TV。
