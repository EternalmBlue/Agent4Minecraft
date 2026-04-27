# 插件配置

插件默认配置位于：

```text
src/main/resources/config.yml
```

首次启动后复制到：

```text
plugins/Agent4Minecraft/config.yml
```

## 最小配置

```yaml
backend:
  authToken: "change_me_to_a_strong_token"

plugin:
  language: "zh_CN"
  debug: false
```

通常只需要修改 `backend.authToken`。它必须和后端 `.env` 中的 `RAG_GRPC_AUTH_TOKEN` 完全一致。

## backend

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `backend.authToken` | `EternalBlue` | gRPC Bearer token，生产必须修改 |
| `backend.host` | `127.0.0.1` | 后端 gRPC 主机 |
| `backend.port` | `50051` | 后端 gRPC 端口 |
| `backend.useTls` | `false` | 是否使用 TLS |
| `backend.deadlineMillis` | `15000` | 通用 deadline |
| `backend.probeTimeoutMillis` | `3000` | 启动探测超时 |
| `backend.askDeadlineMillis` | `120000` | 问答请求超时 |
| `backend.syncDeadlineMillis` | `15000` | 同步 RPC 超时 |
| `backend.maxChunkBytes` | `262144` | 文件上传分块大小 |

跨机器示例：

```yaml
backend:
  authToken: "change_me_to_a_strong_token"
  host: "10.0.0.12"
  port: 50051
```

TLS 示例：

```yaml
backend:
  authToken: "change_me_to_a_strong_token"
  host: "backend.example.com"
  port: 50051
  useTls: true
```

## plugin

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `plugin.language` | `zh_CN` | `zh_CN` 或 `en_US` |
| `plugin.debug` | `false` | 是否输出调试日志 |

语言文件会复制到：

```text
plugins/Agent4Minecraft/lang/
```

修改语言文案时优先改运行目录里的 `lang/*.yml`。

## qa

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `qa.rateLimitSeconds` | `3` | 同一玩家连续提问冷却时间 |

关闭冷却：

```yaml
qa:
  rateLimitSeconds: 0
```

## server

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.id` | 服务端根目录名 | 后端识别该 Minecraft 服务端的 ID |

只有在不想使用服务端根目录名时，才显式配置：

```yaml
server:
  id: "production-lobby"
```

约束：

- 不同 Minecraft 服务端应使用不同 `server.id`。
- 不要包含路径分隔符、盘符或空值。
- 改动 `server.id` 会影响后端存储路径和语义记忆作用域。

## 配置校验

插件启动时会校验：

- `backend.authToken` 不为空。
- `backend.port` 在 `1..65535`。
- deadline 和 chunk size 大于 0。
- `qa.rateLimitSeconds >= 0`。
- `server.id` 能从配置或根目录推导出来。

配置错误会导致插件启动失败并自禁用。
