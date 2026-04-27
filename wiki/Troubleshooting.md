# 故障排查

## 插件启动后自禁用

常见原因：

- 后端没有启动。
- `backend.host` 或 `backend.port` 配置错误。
- 后端 gRPC 端口没有监听。
- `backend.authToken` 为空。
- 插件和后端 proto 协议版本不一致。
- `server.id` 已绑定到另一个 `server_instance_id`。

处理：

1. 查看 Paper 控制台中的 Agent4Minecraft 启动日志。
2. 查看后端控制台日志。
3. 确认后端启动命令为 `python main.py`。
4. 确认插件配置的 host / port 指向后端。
5. 确认 token 一致。

## 后端认证失败

插件：

```yaml
backend:
  authToken: "change_me_to_a_strong_token"
```

后端：

```dotenv
RAG_GRPC_AUTH_TOKEN=change_me_to_a_strong_token
```

两个值必须完全一致，包括空格和大小写。

## server.id conflict

含义：后端发现同一个 `server.id` 已经绑定到另一个物理服务端实例。

解决方式：

- 给当前服务器配置新的 `server.id`。
- 或确认旧实例废弃后，停止后端并清理 `data/server_instance_bindings.json` 中对应条目。

不要随意删除插件端 `server-instance-id.txt`，这会让后端认为当前目录是新实例。

## `/askmc` 超时

可能原因：

- 后端首次加载模型或 reranker。
- DeepSeek API 响应慢。
- embedding API 响应慢。
- 检索库较大。
- 网络不稳定。

可临时调大：

```yaml
backend:
  askDeadlineMillis: 180000
```

同时查看后端日志，确认不是 API Key 或向量库错误。

## `/a4m sync` 失败

排查：

- 管理员是否有 `agent4minecraft.admin`。
- 后端是否运行。
- token 是否一致。
- 上传文件是否在允许范围。
- 后端 `mc_servers/` 是否可写。
- 后端日志是否出现 SHA-256、路径校验或刷新错误。

## `/a4m status` 查不到远程状态

可能原因：

- 没有执行过同步。
- 后端重启后内存中的 sync operation 已过期。
- `sync_id` 已超过后端 TTL。
- 后端不可用。

本地仍会显示上次同步摘要，但远程实时状态可能无法查询。

## 修改语言文件不生效

运行时语言文件在：

```text
plugins/Agent4Minecraft/lang/
```

不是源码目录的 `src/main/resources/lang/`。修改后建议重启服务端。

## 插件能启动但没有回答

检查：

- 后端 `data/plugin_docs_vector_db` 是否可用。
- 后端 DeepSeek / 智谱 API Key 是否正确。
- 后端是否能访问外部 API。
- 后端是否返回了空答案或内部异常。
