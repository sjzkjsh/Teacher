# 多模态AI互动式教学智能体 — 后端服务

> 基于 Spring Boot + Spring AI + Ollama 的智能备课平台后端，提供 RAG 知识检索、AI 对话、课件自动生成与迭代修改等核心能力。

---

## 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [已实现功能](#已实现功能)
- [待优化项](#待优化项)
- [未来规划](#未来规划)
- [可用技术方案](#可用技术方案)
- [项目结构](#项目结构)
- [API 接口一览](#api-接口一览)
- [快速启动](#快速启动)
- [配置说明](#配置说明)

---

## 项目简介

本项目是"多模态AI互动式教学智能体"的后端服务。教师通过前端与 AI 多轮对话描述教学需求，后端负责：

1. **RAG 知识检索**：从本地知识库中检索相关教学资料，增强 AI 生成质量
2. **智能对话**：基于大模型的多轮对话，主动追问以澄清教学目标、知识要点等
3. **课件生成**：根据教师需求自动生成 PPT 课件和 Word 教案
4. **迭代修改**：理解教师的自然语言修改意见，重新生成课件
5. **版本管理**：每次修改保存版本快照，支持版本回退

### 核心流程

```
教师对话描述需求 → AI 智能追问澄清 → [READY] 需求就绪
                                          ↓
         RAG 检索知识库 + 解析参考资料 → 融合生成 Prompt
                                          ↓
                               大模型生成课件大纲(JSON)
                                          ↓
                            Apache POI 生成 PPT + Word
                                          ↓
                              上传 MinIO → 返回下载链接
                                          ↓
         教师提出修改意见 → [MODIFY] → 重新生成 → 新版本
```

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.5 | 应用框架 |
| Java | 17 | 编程语言 |
| Spring AI | 1.1.5 | AI 集成框架（Chat、Embedding、Document Reader） |
| Ollama | — | 本地大模型推理服务 |
| qwen3:8b | — | 对话与内容生成模型 |
| qwen3-embedding:latest | — | 文本向量化模型 |
| MyBatis Plus | 3.5.11 | 数据库 ORM |
| MySQL | 8.x | 关系型数据库（项目、消息、版本存储） |
| Redis | 7.x | 向量存储（Redis Vector Store）+ 聊天记忆仓库 |
| MinIO | — | 对象存储（PPT/DOCX 文件） |
| Apache POI | 5.5.1 | PPT (.pptx) 和 Word (.docx) 文档生成 |
| Apache Tika | — | 多格式文档内容提取 |
| Spring AI Document Readers | — | PDF、Markdown、JSON 专用读取器 |
| JJWT | 0.11.5 | JWT Token 认证 |
| Easy Captcha | 1.6.2 | 图形验证码生成 |
| SpringDoc OpenAPI | 2.8.6 | Swagger API 文档 |
| Lombok | — | 代码简化 |

---

## 已实现功能

### 1. 本地知识库 RAG

- [x] 启动时自动扫描 `./user-data` 目录加载知识库文档
- [x] 支持 PDF、Markdown、JSON、以及 Tika 通用格式（Word/PPT 等）
- [x] 增量加载：通过 `DocumentStateManager` 检测文件变化，仅处理新增或修改的文件
- [x] 文本分块：`TokenTextSplitter` 按 token 粒度进行语义分块
- [x] 向量化：qwen3-embedding 模型生成向量，存入 Redis Vector Store
- [x] 语义检索：`similaritySearch()` 支持 TopK 和相似度阈值配置
- [x] 对话阶段 RAG：每次对话自动检索相关知识片段（TopK=4, 阈值=0.7）
- [x] 生成阶段 RAG：课件生成时综合检索（TopK=8, 阈值=0.6）
- [x] 资料关联检索：上传资料时自动从知识库检索相关内容辅助分析

### 2. 智能对话与需求收集

- [x] 多轮对话：`ChatClient` + `MessageChatMemoryAdvisor` 实现上下文记忆
- [x] 按用户+项目隔离：`conversationId = courseware_{userId}_{projectId}`
- [x] 持久化记忆：`JdbcChatMemoryRepository` 将对话存入 MySQL
- [x] 窗口记忆：`MessageWindowChatMemory(10)` 保留最近 10 条消息
- [x] 场景一 — 需求收集：AI 主动追问教学目标、知识要点、年级、时长、风格
- [x] 场景二 — 课件修改：AI 分析修改意图，确认后触发 `[MODIFY]` 信号
- [x] `[READY]` 信号：信息充足时输出结构化 JSON 需求
- [x] 参考资料分析：AI 分析上传资料与教学需求的关联性

### 3. 教学意图理解与知识融合

- [x] 多源信息融合：RAG 检索结果 + 用户上传文件内容 + 资料说明 + 对话摘要
- [x] 参考资料文本提取：Apache Tika 解析上传文件
- [x] 生成 Prompt 构建：将全部上下文整合为结构化 Prompt
- [x] 大纲解析：从 AI 返回的 JSON 中解析 `CoursewareOutline` 对象

### 4. 课件生成引擎

- [x] **PPT 生成**（`PptBuilder`）
  - 模板填充模式：读取预设 PPTX 模板，填充标题和要点
  - 从零创建模式：16:9 宽屏，封面页 + 内容页（标题 + 要点 + 讲师备注）
  - 自动回退：模板加载失败时自动切换到从零创建
- [x] **Word 教案生成**（`TeachingPlanBuilder`）
  - 基本信息、教学目标、教学内容（逐页展开）、教学总结
  - 支持讲师备注（教学提示）
- [x] 文件上传至 MinIO 对象存储
- [x] 本地临时文件自动清理

### 5. 迭代优化与版本管理

- [x] 自然语言修改：教师通过对话提出修改意见，AI 重新生成大纲
- [x] `[MODIFY]` 信号：AI 判断需求明确后触发修改流程
- [x] 修改后重新生成 PPT + Word，上传 MinIO
- [x] 版本快照：每次修改保存版本（含大纲 JSON、文件路径、修改指令）
- [x] 版本列表查询
- [x] 版本回退：恢复到指定版本的文件引用

### 6. 用户认证与项目管理

- [x] 用户注册 / 登录（含图形验证码）
- [x] JWT Token 认证（拦截器 + Token 工具类）
- [x] 项目 CRUD（创建、查询、列表、更新、删除）
- [x] 项目筛选（关键词、学科、状态、时间、排序）
- [x] 项目统计数据（总数、已完成、进行中）
- [x] 对话消息持久化（保存、查询）

---

## 待优化项

### 知识库管理

| 问题 | 现状 | 优化方向 |
|------|------|---------|
| 无管理 API | 知识库文件需手动放到服务器文件系统 | 提供知识库文档的上传/删除/列表 API |
| 旧向量未清理 | 重新处理文件时未删除旧向量 | 基于 metadata source 字段实现旧向量删除 |
| 无检索质量反馈 | 无法了解 RAG 检索效果 | 返回检索结果的相关度评分 |

### 多媒体内容理解

| 问题 | 现状 | 优化方向 |
|------|------|---------|
| 图片内容无法分析 | Tika 仅提取文件元数据 | 接入多模态模型（Qwen-VL）进行图片理解 |
| 视频完全无法处理 | 上传后无法利用 | FFmpeg 提取关键帧 + Whisper 音频转文字 |
| 文档引用粒度粗 | 全文截取前 3000 字符 | 章节/段落级精确引用 |

### 课件生成质量

| 问题 | 现状 | 优化方向 |
|------|------|---------|
| PPT 纯文本 | 只有文字和要点 | 插入图片、图表、SmartArt |
| 无目录/总结页 | 所有页面样式一致 | 自动插入目录页和总结页 |
| Word 教案不完整 | 缺少教学过程、方法、活动、作业 | 按标准教案模板完善 |
| 无动画/小游戏 | 完全未实现 | AI 生成 HTML5 动画/互动游戏代码 |

### 对话与系统

| 问题 | 现状 | 优化方向 |
|------|------|---------|
| 生成阻塞 | 同步等待，5分钟超时 | 引入消息队列异步处理 |
| 无向量库隔离 | 全局共享一个 index | 按用户/项目隔离命名空间 |
| 模型能力有限 | qwen3:8b 推理速度一般 | 升级模型或增加 GPU |

---

## 未来规划

### 短期（核心功能补全）

- [ ] **动画与互动小游戏生成**
  - 大模型生成 HTML5 Canvas / CSS Animation 代码
  - 支持选择题、拖拽排序、连线题等互动形式
  - 导出为独立 HTML5 网页或嵌入 PPT
- [ ] **PPT 增强**
  - 自动生成目录页和总结页
  - 图文混排、流程图、对比表等多样化布局
- [ ] **Word 教案完善**
  - 教学过程（导入→新授→练习→巩固→总结）
  - 教学方法、课堂活动设计、课后作业

### 中期（多模态能力提升）

- [ ] **多模态模型集成**：Qwen-VL / LLaVA 图片理解
- [ ] **视频处理**：FFmpeg 关键帧 + Whisper ASR
- [ ] **知识库管理 API**：上传、删除、分类、搜索
- [ ] **智能配图**：AI 生图或图库检索
- [ ] **TTS 语音讲解**：为课件生成语音

### 长期（平台化）

- [ ] 异步任务队列（RabbitMQ）
- [ ] 课堂实时互动（WebSocket）
- [ ] 教学效果分析
- [ ] 多学科标准课标知识库
- [ ] 协作备课

---

## 可用技术方案

### 多模态理解

| 技术 | 用途 | 说明 |
|------|------|------|
| Qwen-VL / LLaVA | 图片内容理解 | 开源视觉语言模型，Ollama 可部署 |
| Whisper | 音频/视频转文字 | 高精度 ASR，支持中英文 |
| FFmpeg | 视频关键帧提取 | Java 可通过 process 调用 |
| EasyOCR / PaddleOCR | 图片 OCR | 开源 OCR 引擎 |

### 课件增强

| 技术 | 用途 | 说明 |
|------|------|------|
| Apache POI 高级 API | 图表/SmartArt/图片插入 | 当前仅用基础文本功能 |
| Mermaid CLI | 流程图/思维导图 | 生成 SVG/PNG 嵌入 PPT |
| Canvas / PixiJS | HTML5 动画 | AI 生成代码，后端渲染截图或输出 HTML |
| Matter.js | 物理引擎小游戏 | 力学实验模拟等 |

### 系统优化

| 技术 | 用途 | 说明 |
|------|------|------|
| RabbitMQ / Kafka | 异步任务队列 | 解决生成阻塞 |
| Milvus / Qdrant | 专业向量数据库 | 更大规模、更好隔离 |
| LangChain4j | Java AI 框架 | 更成熟的 RAG 和 Agent 支持 |
| Elasticsearch | 全文检索 | 与向量检索混合，提高召回 |
| Docker Compose | 容器化部署 | 统一管理所有中间件 |
| SSE / WebSocket | 实时进度推送 | 替代前端轮询 |

### 大模型选型

| 模型 | 参数量 | 特点 |
|------|--------|------|
| qwen3:8b（当前） | 8B | 轻量级，本地可跑 |
| qwen3:14b | 14B | 更强推理，需更多显存 |
| qwen2.5-coder:7b | 7B | 代码生成强，适合动画/游戏 |
| deepseek-r1:8b | 8B | 推理能力强 |
| qwen-vl | 7B | 视觉语言模型，图片理解 |

---

## 项目结构

```
AITeacher/
├── src/main/java/com/example/aiteacher/
│   ├── AiTeacherApplication.java             # 启动类
│   │
│   ├── Config/                                # 配置类
│   │   ├── ChatConfig.java                    #   AI ChatClient + 记忆 Advisor
│   │   ├── VectorStoreConfig.java             #   Redis 向量库 Bean
│   │   ├── DataLoader.java                    #   知识库文档加载与向量化（@Async）
│   │   ├── MinioConfig.java                   #   MinIO 客户端配置
│   │   ├── AsyncConfig.java                   #   异步线程池配置
│   │   ├── WebConfig.java                     #   Web 配置（CORS 等）
│   │   ├── AuthenticationInterceptor.java     #   JWT 认证拦截器
│   │   ├── MybatisPlusConfig.java             #   MyBatis Plus 配置
│   │   └── DataLoader.java                    #   知识库文档加载
│   │
│   ├── Controller/                            # 控制器层
│   │   ├── CoursewareController.java          #   课件生成/修改/版本/下载 API
│   │   ├── ProjectController.java             #   项目 CRUD + 消息管理 API
│   │   ├── LoginController.java               #   登录/注册/验证码 API
│   │   └── UserController.java                #   用户信息 API
│   │
│   ├── Service/                               # 服务层
│   │   ├── loginService.java                  #   登录服务接口
│   │   └── ServiceImpl/
│   │       ├── CoursewareService.java         #   ★ 核心服务：AI 对话、RAG、课件生成
│   │       ├── ProjectService.java            #   项目管理服务
│   │       ├── MinioService.java              #   MinIO 文件上传/下载
│   │       └── loginServiceImpl.java          #   登录服务实现
│   │
│   ├── Mapper/                                # 数据访问层
│   │   ├── ProjectMapper.java                 #   项目表 CRUD
│   │   ├── ChatMessageMapper.java             #   消息表 CRUD
│   │   ├── CoursewareVersionMapper.java       #   版本表 CRUD
│   │   └── loginMapper.java                   #   用户表 CRUD
│   │
│   ├── Entity/                                # 实体类
│   │   ├── Project.java                       #   项目实体
│   │   ├── User.java                          #   用户实体
│   │   ├── ChatMessage.java                   #   对话消息实体
│   │   ├── CoursewareVersion.java             #   课件版本实体
│   │   ├── CoursewareOutline.java             #   课件大纲（AI 输出结构）
│   │   ├── SlideData.java                     #   单页幻灯片数据
│   │   ├── ModifyRequest.java                 #   修改请求 DTO
│   │   ├── ModifyResult.java                  #   修改结果 DTO
│   │   ├── Result.java                        #   统一响应包装
│   │   ├── loginUser.java / loginVo.java      #   登录相关 VO
│   │   ├── CaptchaVo.java                     #   验证码 VO
│   │   ├── UserVo.java                        #   用户 VO
│   │   └── DocumentStateManager.java          #   文档状态管理（增量加载）
│   │
│   └── Util/                                  # 工具类
│       ├── PptBuilder.java                    #   ★ PPT 生成（模板填充 + 从零创建）
│       ├── TeachingPlanBuilder.java           #   ★ Word 教案生成
│       ├── JwtUtil.java                       #   JWT 工具
│       └── LoginUserHolder.java               #   当前登录用户上下文
│
├── src/main/resources/
│   ├── application.yml                        # 配置文件
│   ├── schema.sql                             # 数据库建表脚本
│   ├── create-courseware-version.sql          # 版本表建表脚本
│   ├── fix-missing-columns.sql                # 字段修复脚本
│   └── templates/                             # PPT 模板
│       ├── courseware-template.pptx           #   通用模板
│       ├── 数学.pptx                          #   数学模板
│       ├── 语文.pptx                          #   语文模板
│       └── 英语.pptx                          #   英语模板
│
├── pom.xml                                    # Maven 依赖配置
└── README.md                                  # 本文档
```

---

## API 接口一览

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/login` | 登录（username, password, captchaCode, captchaKey） |
| POST | `/api/register` | 注册（username, password, phone, email） |
| GET | `/api/userInfo` | 获取当前用户信息 |
| GET | `/api/captchaVo` | 获取验证码图片 |

### 项目管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/project/create` | 创建项目 |
| GET | `/api/project/list` | 项目列表（支持 keyword/subject/status 筛选） |
| GET | `/api/project/{id}` | 获取项目详情 |
| PUT | `/api/project/{id}` | 更新项目 |
| DELETE | `/api/project/{id}` | 删除项目 |
| POST | `/api/project/{id}/messages` | 保存对话消息 |
| GET | `/api/project/{id}/messages` | 获取项目对话历史 |
| GET | `/api/project/stats` | 获取统计数据 |

### 课件操作

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/courseware/generate` | 生成课件（FormData: topic, slideCount, files, conversationSummary, materialDescriptions） |
| POST | `/api/courseware/chat` | AI 智能对话（JSON: message, projectId, materials） |
| GET | `/api/courseware/detail` | 解析课件大纲（objectName） |
| POST | `/api/courseware/modify` | AI 修改课件（JSON: currentOutline, command, projectId） |
| GET | `/api/courseware/version/list` | 版本列表（projectId） |
| POST | `/api/courseware/version/restore` | 版本回退（versionId） |
| GET | `/api/courseware/download/ppt` | 获取 PPT 下载链接（objectName） |
| GET | `/api/courseware/download/doc` | 获取 Word 下载链接（objectName） |

---

## 快速启动

### 环境依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Maven | 3.8+ | 构建工具 |
| MySQL | 8.x | 数据库 |
| Redis | 7.x | 向量存储 + 聊天记忆 |
| MinIO | — | 对象存储 |
| Ollama | — | 本地大模型推理 |

### 1. 准备数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ai_teacher CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

表结构会在启动时自动创建（`spring.sql.init.mode=always`）。

### 2. 准备 Ollama 模型

```bash
ollama pull qwen3:8b
ollama pull qwen3-embedding:latest
```

### 3. 准备知识库文档

将专业知识库文件（PDF、Markdown、Word 等）放入 `./user-data/` 目录。启动时会自动加载并向量化。

### 4. 配置修改

编辑 `src/main/resources/application.yml`，根据实际环境修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_teacher
    username: root
    password: 123456       # ← 修改为实际密码
  data:
    redis:
      host: localhost
      port: 6379
      password: infini_rag_flow  # ← 修改为实际密码

minio:
  endpoint: http://localhost:9000
  access-key: rag_flow
  secret-key: infini_rag_flow

app:
  document:
    root-path: ./user-data          # 知识库目录
```

### 5. 启动服务

```bash
cd AITeacher
./mvnw spring-boot:run
```

服务启动后访问 Swagger 文档：`http://localhost:8080/swagger-ui.html`

---

## 配置说明

### application.yml 关键配置

```yaml
server:
  port: 8080

spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen3:8b                    # 对话模型
      embedding:
        model: qwen3-embedding:latest      # 向量化模型
    vectorstore:
      redis:
        initialize-schema: true            # 自动创建向量索引
        index: my-ai-index                 # 索引名称
        prefix: "doc:"                     # Key 前缀

app:
  document:
    root-path: ./user-data                 # 知识库文档目录
    incremental: true                      # 增量加载
    state-file: ./data-loader-state.json   # 状态文件
  courseware:
    output-dir: ./generated-ppts           # PPT 临时输出目录
    template: templates/courseware-template.pptx  # PPT 模板
    bucket: courseware                     # MinIO 存储桶
```

### 数据库表结构

| 表名 | 用途 |
|------|------|
| `project` | 备课项目（标题、学科、年级、状态、文件引用等） |
| `chat_message` | 对话消息（角色、内容、关联项目、额外数据） |
| `SPRING_AI_CHAT_MEMORY` | Spring AI 对话记忆持久化 |
| `courseware_version` | 课件版本快照（大纲 JSON、文件路径、修改指令） |

