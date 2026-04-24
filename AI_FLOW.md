# AIVideo 全链路流程（上传 -> AI）

## 1. 上传阶段（必须先做）

1. 前端上传本地视频：`POST /media/upload`，或上传链接：`POST /media/upload-url`。
2. `MediaController` 接收请求，调用 `MinioUtils` 上传到 MinIO。
3. 后端写入 `media_files` 表（`filename`、`filePath`、`status`、`uploadTime`、`userId` 等）。
4. 删除用户列表缓存：`media:list:user:{userId}`，避免前端读到旧列表。
5. 前端调用 `GET /media/list?userId=...` 获取最新任务列表和 `mediaId`。

## 2. 点击 AI 后的提交阶段

1. 前端拿到 `mediaId` 后，调用 `GET /mission/ai?id={mediaId}`。
2. 进入 `AiController.aiAnalyze(...)`：
   - 使用 Redisson 分布式锁 `lock:analyze:{id}`，防重复并发提交。
   - 使用 Redisson 限流器 `limit:ai:global`，全局限流（每分钟最多 10 次）。
   - 查询 `media_files`，确认记录存在。
   - 先把 `aiSummary` 更新为“已进入消息队列，等待调度”。
   - 删除缓存 `media:list:user:{userId}`。
   - 发送 RocketMQ 消息到 `video-analysis-topic`。
3. 接口立即返回，任务进入后台异步执行。

## 3. MQ 消费与线程池执行

1. `VideoAnalyzeConsumer` 监听 topic：`video-analysis-topic`（group：`video-group`）。
2. 收到消息后，在 `onMessage(...)` 中用
   `CompletableFuture.runAsync(..., aiTaskExecutor)` 提交到线程池。
3. 线程池任务执行 `AiService.asyncAnalyze(mediaId)`。

## 4. AI 处理阶段（AiService）

`asyncAnalyze(mediaId)` 的核心顺序：

1. 按 `mediaId` 查询 `MediaFile`。
2. 调用 `aiStrategy.transcribe(filePath)` 做转写。
3. 调用 `aiStrategy.generateSummary(filePath)` 做总结。
4. 回写数据库：`transcriptText`、`aiSummary`。
5. 再次删除缓存 `media:list:user:{userId}`，保证前端轮询列表可见最新结果。

异常时也会清缓存，避免前端一直看到旧状态。

## 5. 转写-only 链路（不走 MQ）

调用 `GET /mission/transcribe?id={mediaId}`：

1. `AiController.transcribe(...)` 直接调用 `AiService.asyncTranscribe(...)`。
2. `asyncTranscribe(...)` 带 `@Async("aiTaskExecutor")`，异步在线程池执行。
3. 仅更新 `transcriptText`，并删除用户列表缓存。

## 6. Redis 在此系统中的角色

- `lock:analyze:{id}`：分布式锁，防重复提交。
- `limit:ai:global`：全局限流状态。
- `media:list:user:{userId}`：媒体列表缓存（`/media/list` 的缓存键）。

策略：数据有变更（上传、AI处理中、AI完成、删除）就删该用户缓存。

## 7. 线程池与线程模型

线程池定义：`ThreadPoolConfig.aiTaskExecutor`

- corePoolSize = 4
- maxPoolSize = 8
- queueCapacity = 100
- threadNamePrefix = `AI-Thread-`
- rejected policy = `CallerRunsPolicy`

线程分工：

1. HTTP 请求线程：处理上传、提交 AI 请求。
2. MQ 消费线程：接收消息并投递任务。
3. `AI-Thread-*`：真正执行耗时 AI 任务。

当线程池满时，`CallerRunsPolicy` 会让提交线程自己执行任务，不丢任务但吞吐会下降。

## 8. 视频转音频去重后的实现

已统一到 `MediaService.convertToMp3(...)`：

- `MediaController /media/download` 使用该方法生成下载音频。
- `AiStrategyImpl` 在转写前也使用该方法提取音频（单声道 + 16kHz）。

这样避免 `AiStrategyImpl` 和 `MediaService` 两处重复维护 ffmpeg 命令。
