# 后端集成

插件通过 gRPC 与 AgentForMc 后端通信。

## 关联协议

插件端：

```text
src/main/proto/agent_bridge.proto
```

后端：

```text
agent_for_mc/interfaces/grpc/agent_bridge.proto
```

两个文件必须保持兼容。

## 服务

```proto
service AgentBridgeService {
  rpc Probe(ProbeRequest) returns (ProbeResponse);
  rpc Ask(AskRequest) returns (AskResponse);
  rpc PrepareSync(SyncPrepareRequest) returns (SyncPrepareResponse);
  rpc UploadFileChunk(stream FileChunkUploadRequest) returns (FileChunkUploadResponse);
  rpc CommitSync(SyncCommitRequest) returns (SyncCommitResponse);
  rpc GetSyncStatus(SyncStatusRequest) returns (SyncStatusResponse);
}
```

## 认证

业务 RPC 都需要：

```text
authorization: Bearer <token>
```

插件配置：

```yaml
backend:
  authToken: "change_me_to_a_strong_token"
```

后端配置：

```dotenv
RAG_GRPC_AUTH_TOKEN=change_me_to_a_strong_token
```

`Probe` 当前不带认证，用于启动阶段发现后端和协议状态，但仍会校验服务端身份。

## 启动探测

插件启用时会调用 `Probe`，传入：

- `server_id`
- `server_instance_id`
- `plugin_name`
- `plugin_version`
- `protocol_version`

后端返回：

- `ack`
- `message`
- `backend_name`
- `backend_version`
- `protocol_version`

如果协议版本不匹配、后端不可达或 `server.id` 冲突，插件会自禁用。

## Deadline

| 调用 | 配置 |
| --- | --- |
| `Probe` | `backend.probeTimeoutMillis` |
| `Ask` | `backend.askDeadlineMillis` |
| `PrepareSync` / `CommitSync` / `GetSyncStatus` | `backend.syncDeadlineMillis` |
| `UploadFileChunk` | `backend.syncDeadlineMillis` + 本地等待余量 |

问答默认更长，因为后端可能需要检索、规划和调用模型。

## 错误映射

| gRPC 状态 | 插件展示 |
| --- | --- |
| `UNAUTHENTICATED` | 后端认证失败 |
| `PERMISSION_DENIED` | 后端拒绝执行 |
| `DEADLINE_EXCEEDED` | 请求超时 |
| `UNAVAILABLE` | 后端不可用 |
| `INVALID_ARGUMENT` | 请求被拒绝 |
| `FAILED_PRECONDITION` | 当前状态不满足，例如 `server.id conflict` |

## 协议变更规则

变更 proto 时必须同步：

- 插件端 proto。
- 后端 proto。
- 后端生成的 Python gRPC 代码。
- 插件端 Gradle proto 生成。
- 插件和后端的 gRPC 测试。

不要在插件里添加猜测性的 REST 或 WebSocket fallback。当前传输契约是 gRPC。
