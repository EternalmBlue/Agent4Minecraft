# 快速开始

本页用于从零完成一次本地联调：启动 AgentForMc 后端、构建插件、安装到 Paper 服务端，并在游戏内执行一次问答和配置同步。

## 前置条件

插件端：

- JDK 21
- Paper 1.20.4+ 或兼容服务端
- Windows PowerShell 或其他 shell

后端：

- Python 3.11+
- DeepSeek API Key
- 智谱 embedding API Key
- 已准备 `data/plugin_docs_vector_db` 插件文档向量库

## 克隆仓库

```powershell
git clone https://github.com/EternalmBlue/AgentForMc.git
git clone https://github.com/EternalmBlue/Agent4Minecraft.git
```

## 启动后端

```powershell
cd AgentForMc
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
```

编辑 `AgentForMc/.env`：

```dotenv
RAG_ZHIPU_API_KEY=你的智谱APIKey
RAG_DEEPSEEK_API_KEY=你的DeepSeekAPIKey
RAG_GRPC_AUTH_TOKEN=change_me_to_a_strong_token
```

启动：

```powershell
python main.py
```

默认监听 `127.0.0.1:50051`。

## 构建插件

```powershell
cd Agent4Minecraft
.\gradlew.bat clean test build
```

部署这个文件：

```text
build/libs/Agent4Minecraft-1.0-SNAPSHOT.jar
```

不要部署 `plain` 后缀的 jar。

## 安装插件

1. 停止 Paper 服务端。
2. 把 `Agent4Minecraft-1.0-SNAPSHOT.jar` 放进服务端 `plugins/`。
3. 启动一次服务端，让插件生成默认配置。
4. 停止服务端。
5. 编辑 `plugins/Agent4Minecraft/config.yml`。
6. 再次启动服务端。

最小配置：

```yaml
backend:
  authToken: "change_me_to_a_strong_token"
```

这个 token 必须和后端 `RAG_GRPC_AUTH_TOKEN` 一致。

## 验证联通

启动 Paper 服务端后，插件日志应出现后端探测成功信息。然后在游戏内执行：

```text
/a4m sync
/a4m status
/askmc eco 插件的金币倍率在哪里配置？
```

## 成功标准

- 插件没有在启动阶段自禁用。
- `/a4m sync` 能返回 syncId 和上传统计。
- `/a4m status` 能看到后端状态。
- `/askmc` 能返回 AI 回答。

如果失败，优先查看：

- 后端是否还在运行。
- 插件 `backend.authToken` 是否与后端 `RAG_GRPC_AUTH_TOKEN` 一致。
- 插件 `backend.host` / `backend.port` 是否正确。
- 后端 `data/plugin_docs_vector_db` 是否存在且结构正确。
