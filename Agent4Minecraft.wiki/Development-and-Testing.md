# 开发与测试

## 构建

```powershell
.\gradlew.bat clean test build
```

## 常用命令

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat clean
```

## 技术栈

- Kotlin JVM `2.2.21`
- Gradle `8.14`
- Paper API `1.20.4-R0.1-SNAPSHOT`
- gRPC `1.71.0`
- protobuf `3.25.5`
- Shadow Jar

开发使用 JDK 21 toolchain，但 Java 和 Kotlin 字节码目标保持 Java 17，以兼容 Paper 1.20.4+。

## 测试范围

重点测试：

- `PluginSettingsLoader`
- `ServerInstanceIdStore`
- `StartupProbeVerifier`
- `GrpcBackendClient`
- `AsyncFailureUnwrapper`
- `SensitiveConfigRedactor`
- `SyncUploadPreparer`
- `SyncService`
- i18n 消息加载

## gRPC 测试原则

本仓库优先使用：

```text
InProcessServerBuilder
InProcessChannelBuilder
```

这样可以覆盖真实 generated stub、metadata、deadline、streaming 行为。

不要用手写 mock client 替代传输契约测试，除非明确只是在测调用方分支逻辑。

## 手工验证建议

每次改动后至少验证：

1. `.\gradlew.bat clean test build`
2. 启动 AgentForMc 后端。
3. 启动本地 Paper 服务端。
4. 插件启动探测成功。
5. `/a4m sync` 成功。
6. `/a4m status` 能看到远程状态。
7. `/askmc <问题>` 能返回答案。
8. 手动关闭后端后，插件错误提示可读。

## 修改 build.gradle.kts 时注意

- Java 和 Kotlin target 都要保持 `17`。
- gRPC、protobuf、shadow relocation 需要一起验证。
- 修改依赖后运行完整 `clean test build`。
- 不要引入会与 Paper 服务端冲突的未 relocated 依赖。

## 修改命令逻辑时注意

- 不要阻塞主线程。
- 网络调用必须在线程池。
- 回到 Bukkit API 操作时切回主线程。
- 保持玩家可读错误提示。
- 权限检查放在命令入口。

## 修改同步逻辑时注意

- 保持允许列表策略。
- 不上传整个服务器。
- 校验相对路径不能逃逸根目录。
- checksum 基于实际上传字节。
- 脱敏只影响上传副本。
- 后端刷新可能晚于本地上传完成，状态查询要继续使用最近的 syncId。
