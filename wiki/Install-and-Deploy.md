# 安装与部署

## 构建产物

执行：

```powershell
.\gradlew.bat clean test build
```

最终服务端部署包：

```text
build/libs/Agent4Minecraft-1.0-SNAPSHOT.jar
```

`build/libs/Agent4Minecraft-1.0-SNAPSHOT-plain.jar` 是未打包依赖的普通 jar，不适合作为服务端插件部署包。

## 安装步骤

1. 停止 Minecraft 服务端。
2. 将插件 jar 放入 `plugins/`。
3. 启动服务端一次，生成配置。
4. 停止服务端。
5. 修改 `plugins/Agent4Minecraft/config.yml`。
6. 启动后端 AgentForMc。
7. 启动 Minecraft 服务端。

## 首次启动生成的文件

```text
plugins/Agent4Minecraft/config.yml
plugins/Agent4Minecraft/server-instance-id.txt
plugins/Agent4Minecraft/lang/zh_CN.yml
plugins/Agent4Minecraft/lang/en_US.yml
```

说明：

- `config.yml`：插件运行配置。
- `server-instance-id.txt`：物理服务器实例 ID，不建议手动改。
- `lang/*.yml`：游戏内显示文案，可按需修改。

## server.id 与 server-instance-id

`server.id` 是后端和运维看到的 Minecraft 服务端名称。默认情况下，插件会从服务端根目录名推导。

`server-instance-id.txt` 是插件自动生成的物理实例 ID，用于防止不同 Minecraft 服务端误用同一个 `server.id`。

如果同一个 `server.id` 被另一个实例使用，后端会返回 `server.id conflict`，插件启动探测失败并自禁用。

## 跨机器部署

后端 `config.toml`：

```toml
[grpc]
host = "0.0.0.0"
port = 50051
```

插件 `config.yml`：

```yaml
backend:
  authToken: "change_me_to_a_strong_token"
  host: "后端机器IP"
  port: 50051
```

生产环境建议通过防火墙限制来源 IP，并评估 TLS / mTLS。

## 升级插件

1. 停止服务端。
2. 备份 `plugins/Agent4Minecraft/`。
3. 替换 `plugins/Agent4Minecraft-*.jar`。
4. 启动服务端。
5. 检查启动探测日志。
6. 执行 `/a4m status` 和 `/askmc <问题>` 验证。

不要删除 `server-instance-id.txt`，除非你明确要让后端把当前目录视为新的物理服务端实例。

## 回滚

1. 停止服务端。
2. 替换回旧版 jar。
3. 恢复备份的 `plugins/Agent4Minecraft/config.yml` 和 `lang/`。
4. 保留 `server-instance-id.txt`。
5. 启动服务端。

如果回滚涉及 proto 或后端协议版本，也要同步回滚 AgentForMc。
