# 命令与权限

## 命令列表

| 命令 | 权限 | 默认权限 | 说明 |
| --- | --- | --- | --- |
| `/askmc <问题>` | `agent4minecraft.ask` | 所有人 | 向后端发起问答 |
| `/a4m sync` | `agent4minecraft.admin` | OP | 手动同步服务器配置 |
| `/a4m status` | `agent4minecraft.admin` | OP | 查询同步状态 |

## `/askmc <问题>`

示例：

```text
/askmc Residence 怎么设置玩家可购买的最大领地数量？
/askmc eco 插件的金币倍率在哪里配置？
```

插件会收集：

- 玩家 UUID 或控制台 sender 名称。
- 玩家显示名。
- 原始问题。
- 当前服务端 `server.id`。
- 当前服务端 `server_instance_id`。
- 已安装插件名称、版本和启用状态。

然后异步调用后端 `Ask`。

## `/a4m sync`

示例：

```text
/a4m sync
```

流程：

1. 扫描允许上传的配置文件。
2. 生成 manifest。
3. 对敏感值生成脱敏上传副本。
4. 调用后端 `PrepareSync`。
5. 上传后端要求的变更文件。
6. 调用 `CommitSync`。
7. 后端启动语义刷新。

该命令可能耗时较长，但执行在线程池中，不阻塞 Minecraft 主线程。

## `/a4m status`

示例：

```text
/a4m status
```

返回信息包括：

- 后端 endpoint。
- 本地同步是否空闲。
- 本地同步阶段和进度。
- 上次同步摘要。
- 远程上传进度。
- 远程语义刷新状态。
- 上次失败原因。

## 权限建议

普通玩家：

```text
agent4minecraft.ask
```

管理员：

```text
agent4minecraft.ask
agent4minecraft.admin
```

建议只给可信管理员 `agent4minecraft.admin`，因为 `/a4m sync` 会把允许范围内的服务端配置同步给后端。

## 控制台使用

控制台也可以执行：

```text
askmc <问题>
a4m sync
a4m status
```

控制台的 sender id 会按名称生成，不使用玩家 UUID。
