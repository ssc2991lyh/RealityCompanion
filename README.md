# Reality Companion - 手机陪伴终端

对接 AstrBot `reality_companion` 插件移动端网关的 Android 客户端。

## 功能

- **配对连接** — 输入服务器地址和配对令牌，换取会话令牌
- **摄像头单帧** — 向服务器请求一次摄像头画面（不保存）
- **位置上报** — 获取 GPS/网络位置并上报到网关
- **健康数据** — 上报心率、步数等遥测数据
- **活动状态** — 上报当前活动类型和时长
- **断开连接** — 主动关闭会话

## 前置条件

1. **电脑上已安装 AstrBot** + `reality_companion` 插件
2. 插件配置中开启 `mobile.enabled = true`
3. 设置好 `mobile.host`（电脑局域网 IP）、`mobile.port`（默认 6322）、`pairing_token`
4. 电脑和手机在同一局域网

## 构建 APK

### 方式一：Android Studio 构建（推荐）

1. 用 Android Studio 打开本目录
2. 等待 Gradle 同步完成
3. 菜单栏 → Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`

### 方式二：命令行构建

```bash
# 进入项目目录
cd RealityCompanion

# 构建 Debug APK
./gradlew assembleDebug

# APK 输出
app/build/outputs/apk/debug/app-debug.apk
```

> 需要预先安装 JDK 17 和 Android SDK。

## 使用

1. 打开 App，填入服务器地址，如 `http://192.168.1.100:6322`
2. 填入配对令牌（在 AstrBot 私聊中发送 `现实触及 配对令牌` 查看）
3. 点击「连接」
4. 配对成功后即可使用各项功能

## 网关 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/pair` | POST | 配对（交换令牌） |
| `/location` | POST | 上报位置 |
| `/device/status` | POST | 设备状态/摄像头 |
| `/device/activity` | POST | 活动状态 |
| `/telemetry` | POST | 健康遥测 |
| `/screen/heartbeat` | POST | 屏幕心跳 |
| `/session/close` | POST | 关闭会话 |

## 隐私说明

- 会话令牌本地加密存储
- 位置数据仅上报到你的 AstrBot 服务器
- 摄像头仅单帧读取，不保存到本地