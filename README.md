<div align="center">
  <h1 align="center">AIVideo - 智能视频分析工作台</h1>

  <p align="center">
    <strong>视频上传 / 音频下载 / 文字提取 / 全文总结 / 异步任务处理</strong>
  </p>

  <p align="center">
    <img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen" alt="Spring Boot" />
    <img src="https://img.shields.io/badge/Vue-3.5-42b883" alt="Vue" />
    <img src="https://img.shields.io/badge/RocketMQ-4.9-orange" alt="RocketMQ" />
    <img src="https://img.shields.io/badge/Redis-Cache-red" alt="Redis" />
    <img src="https://img.shields.io/badge/MinIO-Object%20Storage-blue" alt="MinIO" />
    <img src="https://img.shields.io/badge/FFmpeg-Media-black" alt="FFmpeg" />
    <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
  </p>
</div>

<br/>

**AIVideo** 是一个面向视频内容处理场景的全链路 AI 平台，支持本地视频上传、链接上传、音频提取、文字转写和全文总结。项目基于前后端分离架构构建，结合 **RocketMQ + Redis + MinIO + FFmpeg** 实现长任务异步化、资源解耦和结果可视化展示。

项目重点不只是“上传视频”，而是把视频进一步转化为可检索、可阅读、可分析的结构化内容，让视频从“存储载体”变成“可理解的数据资源”。

<br/>

## 项目预览

<div align="center">
  <h3>首页工作台</h3>
  <img width="100%" alt="网页首页" src="https://github.com/user-attachments/assets/22b26634-983e-404c-a304-d6b69cb2fd2b" />
</div>

<br/>

<div align="center">
  <table>
    <tr>
      <td align="center" width="50%">
        <h3>登录界面</h3>
        <img width="100%" alt="登录" src="https://github.com/user-attachments/assets/65fef7da-5909-417c-900d-e2fdd0bfda5c" />
      </td>
      <td align="center" width="50%">
        <h3>注册界面</h3>
        <img width="100%" alt="注册" src="https://github.com/user-attachments/assets/48ff3215-5b90-4d54-8599-5b769022ed7c" />
      </td>
    </tr>
  </table>
</div>

<br/>

<div align="center">
  <h3>功能展示</h3>
  <img width="100%" alt="功能展示" src="https://github.com/user-attachments/assets/f78208ba-ae34-4035-869c-80099c141beb" />
</div>

<br/>

## 核心功能

### 1. 视频上传

- 支持本地文件上传
- 支持拖拽上传
- 支持视频链接上传
- 上传成功后自动写入媒体记录，并刷新当前用户的视频列表

### 2. 视频工作台

- 展示当前用户的所有上传视频
- 支持选中某个视频进入操作区
- 支持删除已上传视频
- 支持轮询任务状态并自动刷新结果

### 3. AI 能力

- 下载音频：将视频转为 mp3 后下载
- 提取文字：调用 ASR 能力生成全文文字
- 全文总结：调用视觉与文本能力输出长文本总结

### 4. 后端异步处理

- AI 总结任务通过 RocketMQ 异步投递
- 消费者收到消息后，将任务提交到线程池执行
- 使用 Redis 分布式锁避免重复分析同一视频
- 使用 Redis 缓存列表数据，降低高频轮询压力

<br/>



## 技术栈

### 后端

Spring Boot + MyBatis-Plus + MySQL + Redis + Redisson + RocketMQ + MinIO + FFmpeg + yt-dlp

### 前端

Vue 3 + TypeScript + Vite + Axios

### 部署

Docker Compose

<br/>

## 项目结构

```text
AIVideo
├─ client                 # Vue 前端
├─ server                 # Spring Boot 后端
├─ rocketmq               # RocketMQ broker 配置
└─ docker-compose.yml     # 中间件编排
```

<br/>

## 我的开发环境

| 组件 | 版本 | 备注 |
| :--- | :--- | :--- |
| **JDK** | 21 | Spring Boot 3 运行环境 |
| **Maven** | 3.9+ | 后端构建工具 |
| **Node.js** | 20+ | 前端构建依赖 |
| **MySQL** | 8.0 | Docker 镜像 `mysql:8.0` |
| **Redis** | 7.x | Docker 镜像 `redis:7` |
| **RocketMQ** | 4.9.4 | Docker 镜像 `apache/rocketmq:4.9.4` |
| **MinIO** | Latest | 对象存储 |
| **FFmpeg** | 本地可执行版本 | 音视频处理 |
| **yt-dlp** | 最新版 | 视频链接抓取 |

<br/>

## 如何本地部署

### 1. 启动中间件

本项目依赖多个中间件，统一通过 `docker-compose.yml` 管理：

```bash
docker compose up -d
```

当前编排内容包括：

- MySQL
- Redis
- MinIO
- RocketMQ NameServer
- RocketMQ Broker
- RocketMQ Dashboard

### 2. 配置后端参数

后端配置文件位于：

```text
server/src/main/resources/application.properties
```

需要重点确认这些配置：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/media_db...
spring.datasource.username=root
spring.datasource.password=123456

minio.endpoint=http://localhost:9000
minio.accesskey=minioadmin
minio.secretkey=minioadmin
minio.bucketname=media

spring.data.redis.host=localhost
spring.data.redis.port=6379

rocketmq.name-server=127.0.0.1:19876
rocketmq.producer.group=video-producer-group

tool.ytdlp.path=E:/Apps/yt-dlp/yt-dlp.exe
tool.ffmpeg.dir=E:/Apps/ffmpeg-8.0.1-essentials_build/bin
```

如果需要调用 AI 接口，还需要配置：

```properties
ai.api-key=${DASHSCOPE_API_KEY:}
```

也可以通过系统环境变量注入：

```bash
DASHSCOPE_API_KEY=你的密钥
```

### 3. 启动后端

```bash
cd server
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:9090
```

### 4. 启动前端

```bash
cd client
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

<br/>

## 数据表结构

项目中的 `media_files` 表至少需要这些字段：

```sql
CREATE TABLE media_files (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  filename VARCHAR(255) NOT NULL,
  status VARCHAR(50) NULL,
  file_path VARCHAR(512) NULL,
  ai_summary LONGTEXT NULL,
  transcript_text LONGTEXT NULL,
  cover_url VARCHAR(512) NULL,
  upload_time DATETIME NULL
);
```

如果旧表缺少字段，可以执行：

```sql
ALTER TABLE media_files
  ADD COLUMN transcript_text LONGTEXT NULL,
  ADD COLUMN ai_summary LONGTEXT NULL,
  ADD COLUMN cover_url VARCHAR(512) NULL;
```

<br/>

