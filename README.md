## MinecraftLanRedirect

一个用于 **将本地 Minecraft LAN 连接透明转发到远程服务器** 的小工具，同时在局域网中伪装出一个 “LAN 世界”，让局域网内的客户端像加入本地世界一样进入远程服务器。

### 功能概览

- **TCP 端口转发**：  
  - 将本机某个监听端口（如 `localhost:666` 或 `localhost:9099`）收到的 Minecraft 连接转发到配置中的远程服务器 `remote.host:remote.port`。
- **LAN 伪装广播**：  
  - 按一定间隔在局域网中通过 UDP 广播一个假的 LAN 服务器信息（MOTD、版本号、最大玩家数等），  
  - 让所有在同一局域网中的客户端在 “多人游戏 → 局域网世界(LAN)” 列表里看到该服务器并可一键加入。
- **多来源配置加载**：  
  - 启动时按优先级自动查找 `config.yaml`：  
    1. 启动参数指定的路径  
    2. **Jar 同目录** 下的 `config.yaml`  
    3. **当前工作目录** 下的 `config.yaml`  
  - 若都不存在，则自动在 Jar 同目录生成一个默认模板并提示用户修改。
- **IP 白名单安全控制（可选）**：  
  - 支持 IPv4 / IPv6 CIDR 段形式的白名单（如 `192.168.0.0/24`、`fd00::/8`），  
  - 未配置白名单时允许所有局域网客户端连接。
- **远程凭证支持（可选）**：  
  - 可配置 `credentials.enabled` 与 `credentials.token`，在需要身份验证的远程代理/服务器前增加简单鉴权信息。
- **简单日志系统**：  
  - 基于 `slf4j-simple`，支持在 `config.yaml` 中设置日志级别（`TRACE/DEBUG/INFO/WARN/ERROR`），  
  - 默认显示较为详细的启动过程、转发目标、LAN 广播信息和安全提示等。

### 使用方法（快速上手）

1. **构建可执行 Jar（可选，若你已生成则跳过）**
   - 项目基于 Maven，直接在项目目录执行：

```bash
mvn clean package
```

   - 在 `target` 目录下会生成一个带依赖的可执行 Jar（由 `maven-shade-plugin` 打包）。

2. **生成并编辑配置文件**
   - 第一次运行时，如果找不到 `config.yaml`，程序会自动在 **Jar 同目录** 生成一个模板：  

```bash
java -jar MinecraftLanRedirect.jar
```

   - 控制台会提示生成的配置路径，你只需打开并根据注释修改：
     - `remote.host` / `remote.port`：远程 Minecraft 服务器或代理地址与端口  
     - `local.listenPort`：本地客户端要连接的端口  
     - `lan.motd` / `lan.version` / `lan.maxPlayers`：在 LAN 列表中展示的伪装服务器信息  
     - `lan.broadcastAddress` / `lan.broadcastPort` / `lan.announceIntervalMs`：LAN 广播相关参数  
     - `security.whitelist`：需要时开启 IP 白名单  
     - `credentials.enabled` / `token`：远程需要鉴权时填写  
     - `logging.level`：日志级别（默认 `INFO`）

3. **正式运行**

```bash
java -jar MinecraftLanRedirect.jar                # 使用 Jar 同目录或工作目录下的 config.yaml
java -jar MinecraftLanRedirect.jar ./config.yaml  # 或显式指定配置文件路径
```

控制台会打印类似信息：

- 本地监听端口与远程转发目标  
- LAN 广播使用的 MOTD、版本、人数和广播地址/端口  
- IP 白名单启用情况  
- 凭证启用情况  

当你看到类似 **“Velocity LAN 伪装器已启动，按 Ctrl+C 退出。”** 的日志时，说明程序已经正常运行：

- 在同一局域网内的 Minecraft 客户端进入 “多人游戏 → 局域网世界(LAN)” 即可看到配置的伪装服务器；  
- 客户端连接后，实际会被转发到你配置的远程服务器。

### 典型使用场景

- 家庭或网吧环境中，想让局域网玩家方便地加入一个位于公网/其他网络环境的 Velocity/Bungee/纯 Minecraft 服务器。  
- 不方便在每台电脑上单独配置远程地址时，通过 LAN 列表一键加入远程服务器。  
- 需要对 LAN 接入的 IP 做简单的白名单控制，或在接入远程代理时携带一个简单的 token。

# MinecraftLanRedirect

Java 实现的 Velocity LAN 伪装器：将远程 Velocity 代理映射到本机端口，并通过 UDP 广播把它伪装成局域网房间，方便玩家在 “本地游戏” 标签中直接加入。

## 功能
- TCP 端口转发：本地端口 ↔ 远程 Velocity，支持多客户端并发与自动关闭。
- LAN 广播：定时发送 `[MOTD]/[AD]` 数据包，模拟 Minecraft 官方 LAN 广播格式。
- IP 白名单：支持 IPv4/IPv6 CIDR，阻止未授权的局域网访问。
- 灵活配置：YAML 文件集中管理代理地址、广播内容、安全策略和日志等级。

## 快速开始
1. 配置 `config.yaml`（默认位于工作目录，可参见 `src/main/resources/config.yaml` 示例）：
   ```yaml
   remote:
     host: proxy.example.com
     port: 25565
   local:
     listenPort: 25570
   lan:
     motd: "&a远程Velocity代理"
     version: "1.20.4"
     maxPlayers: 20
     announceIntervalMs: 1000
     broadcastPort: 4445
     broadcastAddress: 255.255.255.255
   security:
     whitelist:
       - 192.168.0.0/24
   logging:
     level: INFO
   ```
2. 启动：
   ```powershell
   mvn -q package
   java -jar target/MinecraftLanRedirect-1.0-SNAPSHOT.jar
   ```
   程序会自动查找配置文件，按以下优先级：
   - 如果命令行参数提供了路径，使用指定路径
   - jar 文件同目录下的 `config.yaml`（推荐）
   - 工作目录下的 `config.yaml`
   - 如果都找不到，会在 jar 文件同目录生成配置文件模板并退出
   
   也可以手动指定配置文件路径：
   ```powershell
   java -jar target/MinecraftLanRedirect-1.0-SNAPSHOT.jar /path/to/config.yaml
   ```

## 运行与调试
- **日志级别**：`logging.level` 可设置 `trace`/`debug`/`info`/`warn`/`error`。
- **安全控制**：`security.whitelist` 为空或包含 `any`/`*` 时表示不做限制。
- **LAN 广播**：默认发送到 `255.255.255.255:4445`，如需特定网段可改成子网广播地址。

## 测试
项目目前以编译测试验证：
```powershell
mvn -q test
```
执行成功即可确保依赖与主要逻辑可以正常编译、打包运行。

