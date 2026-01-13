# WSMC: WebSocket Support for Minecraft

[中文说明 (Chinese Version)](#中文说明)

**WSMC** enables WebSocket support for Minecraft Java Edition. By using this mod, server owners can hide their server behind a CDN (content delivery network) that supports WebSocket proxying (e.g., Cloudflare), effectively preventing DDoS attacks by masking the real server IP.

## 🚀 Key Features

*   **DDoS Protection**: Hide your backend server IP behind a CDN.
*   **Dual Protocol Support**: The server handles both vanilla TCP and WebSocket connections on the same listening port.
*   **Backward Compatibility**:
    *   Players with the mod can connect via WebSocket (`ws://` or `wss://`).
    *   Players *without* the mod can still connect via standard TCP (e.g., `ip:port`), provided the server allows it.
*   **Real IP Forwarding**: The server can correctly identify the player's real IP address from HTTP headers (like `X-Forwarded-For`) when behind a proxy.
*   **No Gameplay Changes**: This is a network-layer mod and does not alter gameplay or GUI.
*   **Broad Support**: Available for Forge, NeoForge, and Fabric.

## 📦 Supported Versions

This branch (`main`) primarily targets **1.20.5 and newer**, but the project supports a wide range of versions:

*   **Modern**: 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4
*   **Recent**: 1.20.2, 1.20.3, 1.20.4
*   **Legacy**: 1.20.1, 1.18.2, 1.19.x, 1.20

## 🛠 Installation & Usage

### For Servers
1.  Install the **WSMC** mod into your server's `mods` folder.
2.  Start the server. The mod will automatically listen for WebSocket upgrades on the main server port (defined in `server.properties`).
3.  (Optional) Configure your CDN (e.g., Cloudflare) to proxy traffic to your server's port.

### For Clients
1.  Install the **WSMC** mod into your client's `mods` folder.
2.  To join a WebSocket-enabled server, use the Server Address format:
    *   `ws://hostname.com:port/path`
    *   `wss://hostname.com:port/path` (Secure WebSocket)
3.  To join a standard server, simply use the normal address: `hostname.com:port`.

## ⚙️ Configuration

Configuration is handled via **Java System Properties** (passed with `-D` flags in your JVM startup command).

| Property Key | Type | Default | Side | Description |
| :--- | :--- | :--- | :--- | :--- |
| `wsmc.disableVanillaTCP` | boolean | `false` | Server | If `true`, disables direct TCP logins, forcing all players to use WebSocket (useful for strict CDN enforcement). |
| `wsmc.wsmcEndpoint` | string | *(None)* | Server | Restrict WebSocket connections to a specific path (e.g., `/mc`). Must start with `/`. If unset, any path is accepted. |
| `wsmc.maxFramePayloadLength` | integer | `65536` | Both | Maximum allowed payload size. Increase this if you encounter "Max frame length exceeded" errors with large modpacks. |
| `wsmc.debug` | boolean | `false` | Both | Enable debug logging. |
| `wsmc.dumpBytes` | boolean | `false` | Both | Dump raw WebSocket frames (requires `wsmc.debug=true`). |

**Example Startup Command:**
```bash
java -Dwsmc.disableVanillaTCP=true -Dwsmc.wsmcEndpoint=/minecraft -jar server.jar
```

## 🔗 Advanced Client Connection Options

When connecting via a CDN, you may need to control how the hostname is resolved or what SNI (Server Name Indication) is sent. The client supports complex URI syntaxes:

*   **Basic**: `ws://host.com:port`
*   **Specific IP**: `ws://host.com@1.2.3.4` (Connect to `1.2.3.4` but send `Host: host.com`)
*   **Custom SNI**: `wss://sni-host.com@1.2.3.4` (SNI and Host are `sni-host.com`, connects to `1.2.3.4`)
*   **Different SNI & Host**: `wss://sni.com:host.com@1.2.3.4` (SNI: `sni.com`, Host: `host.com`, Connects to: `1.2.3.4`)

## 💻 For Developers

### Building from Source

1.  Clone the repository.
2.  Navigate to the specific loader directory (`forge`, `neoforge`, or `fabric`).
3.  Run the build command:

**Fabric:**
```bash
cd fabric
./gradlew build
```

**Forge:**
```bash
cd forge
./gradlew build
```

**NeoForge:**
```bash
cd neoforge
./gradlew build
```

---

<a id="中文说明"></a>

# 中文说明 (Chinese Description)

**WSMC** 是一个为 Minecraft Java 版提供 WebSocket 支持的模组。通过本模组，服主可以将服务器部署在支持 WebSocket 代理的 CDN（如 Cloudflare）之后，从而隐藏服务器真实 IP，有效防御 DDoS 攻击。

## 🚀 功能特性

*   **DDoS 防御**: 配合 CDN 使用，隐藏源站 IP。
*   **协议共存**: 服务端在同一个端口上同时处理原版 TCP 请求和 WebSocket 请求。
*   **兼容性**:
    *   安装了本模组的客户端可通过 WebSocket (`ws://` 或 `wss://`) 连接。
    *   **未安装**本模组的客户端仍可通过原版 TCP 方式连接（除非服务端强制禁用 TCP）。
*   **获取真实 IP**: 支持通过 HTTP 头（如 `X-Forwarded-For`）获取玩家的真实 IP 地址。
*   **无感体验**: 本模组仅作用于网络层，不修改游戏玩法或 GUI。
*   **多平台支持**: 支持 Forge, NeoForge 和 Fabric。

## 🛠 安装与使用

### 服务端 (Server)
1.  将模组放入服务端的 `mods` 文件夹。
2.  启动服务器。模组会自动在 `server.properties` 指定的端口上监听 WebSocket 升级请求。
3.  （可选）在 CDN 上配置 WebSocket 转发。

### 客户端 (Client)
1.  将模组放入客户端的 `mods` 文件夹。
2.  在多人游戏中添加服务器，地址格式如下：
    *   `ws://域名.com:端口/路径`
    *   `wss://域名.com:端口/路径` (加密连接)
3.  连接普通服务器仍使用原版格式：`域名.com:端口`。

## ⚙️ 配置文件

本模组通过 **JVM 系统属性**（System Properties）进行配置。请在启动脚本的 `java` 命令中添加 `-D` 参数。

| 属性键名 | 类型 | 默认值 | 作用域 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `wsmc.disableVanillaTCP` | boolean | `false` | 服务端 | 是否禁用原版 TCP 连接。设为 `true` 可强制所有玩家通过 WebSocket (CDN) 进入。 |
| `wsmc.wsmcEndpoint` | string | *(无)* | 服务端 | 指定 WebSocket 连接路径（如 `/mc`）。必须以 `/` 开头。若不设置，则接受任意路径。 |
| `wsmc.maxFramePayloadLength` | integer | `65536` | 双端 | 最大数据帧长度。大型整合包可能需要调大此数值以避免报错。 |
| `wsmc.debug` | boolean | `false` | 双端 | 开启调试日志。 |
| `wsmc.dumpBytes` | boolean | `false` | 双端 | 导出 WebSocket 原始二进制帧（需开启 debug）。 |

**启动命令示例：**
```bash
java -Dwsmc.disableVanillaTCP=true -Dwsmc.wsmcEndpoint=/minecraft -jar server.jar
```

## 🔗 进阶连接选项

当客户端需要自定义 DNS 解析或 SNI 信息时（例如解决 CDN 分配 IP 较慢的问题），可以使用高级 URI 格式：

*   **指定 IP 连接**: `ws://host.com@1.2.3.4` (连接到 IP `1.2.3.4`，但 HTTP Host 头发送 `host.com`)
*   **指定 SNI 和 Host**: `wss://sni-host.com@1.2.3.4`
*   **分离 SNI 和 Host**: `wss://sni.com:host.com@1.2.3.4` (SNI 使用 `sni.com`，Host 使用 `host.com`)

## 💻 开发者指南

### 编译构建

项目包含 `fabric`, `forge`, `neoforge` 三个子项目。

**编译 Fabric 版本:**
```bash
cd fabric
./gradlew build
```

**编译 Forge 版本:**
```bash
cd forge
./gradlew build
```

**注意**: Windows 用户请将命令中的 `./` 替换为 `.\`。编译需安装 JDK 21 (针对 1.20.5+ 版本)。
