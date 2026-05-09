package com.example.aiteacher.Service.ServiceImpl;


import com.example.aiteacher.Entity.*;
import com.example.aiteacher.Mapper.CoursewareVersionMapper;
import com.example.aiteacher.Mapper.ProjectMapper;
import com.example.aiteacher.Util.PptBuilder;
import com.example.aiteacher.Util.TeachingPlanBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.Tika;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CoursewareService {
    private static final Logger log = LoggerFactory.getLogger(CoursewareService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ChatClient coursewareChatClient;
    private final MessageChatMemoryAdvisor coursewareMemoryAdvisor;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;
    private final CoursewareVersionMapper versionMapper;
    private final ProjectMapper projectMapper;
    private final Tika tika;
    private final String outputDir;
    private final String templatePath;
    private final String bucketName;

    public CoursewareService(VectorStore vectorStore,
                             ChatClient.Builder chatClientBuilder,
                             OllamaChatModel ollamaChatModel,
                             ChatMemory chatMemory,
                             ObjectMapper objectMapper,
                             MinioService minioService,
                             CoursewareVersionMapper versionMapper,
                             ProjectMapper projectMapper,
                             @Value("${app.courseware.output-dir:./generated-ppts}") String outputDir,
                             @Value("${app.courseware.template:templates/courseware-template.pptx}") String templatePath,
                             @Value("${app.courseware.bucket:courseware}") String bucketName) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        // 专用于课件对话的记忆 Advisor，按 conversationId 隔离
        this.coursewareMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        // 带记忆的课件对话 ChatClient
        this.coursewareChatClient = ChatClient.builder(ollamaChatModel)
                .defaultSystem("""
                        你是一位资深的AI备课助手，同时负责两个场景：

                        【场景一：需求收集】
                        当教师刚开始备课时，你需要：
                        1. 通过对话理解教师的教学需求
                        2. 主动提问以澄清模糊信息（教学目标、知识要点、年级、时长、风格等）
                        3. 当信息充足时，在回复末尾加上标记 [READY] 并总结需求

                        【场景二：课件修改】
                        当教师在预览已生成的课件时，你需要：
                        1. 分析用户的修改意图，理解要改什么、怎么改
                        2. 如果需求清晰明确，回复 [MODIFY] 并说明将如何修改
                        3. 如果需求模糊，追问具体细节（改哪个页面、改成什么样）

                        通用原则：
                        - 每次只追问1-2个最关键的信息
                        - 用亲切专业的语气，像一位经验丰富的同事在帮忙
                        - 保持对话记忆，根据上下文理解用户的连续意图
                        - 当教师上传了参考资料时，主动分析每份资料与教学需求的关联性，说明资料中哪些内容可以用于教学、如何使用
                        - 在回复中明确指出参考资料的具体用途（如"参考了第3章的实验设计"、"沿用此PDF的排版风格"等）

                        【[READY] 确认格式】
                        当你判断教师的需求已经充分（主题、学科、年级、教学目标都已明确），回复末尾加上 [READY]，
                        然后在下一行输出一个结构化的需求 JSON（用 ```json 代码块包裹），格式如下：
                        ```json
                        {
                          "topic": "教学主题",
                          "subject": "学科",
                          "grade": "年级",
                          "hours": "课时",
                          "goal": "教学目标描述",
                          "keyPoint": "教学重点",
                          "difficulty": "教学难点",
                          "slideCount": 10,
                          "style": "风格描述",
                          "materialAnalysis": "对参考资料的整体分析，说明资料与教学需求的关联"
                        }
                        ```
                        注意：只在信息充足时才输出 [READY]。信息不足时继续追问，不要勉强输出。
                        """)
                .defaultAdvisors(coursewareMemoryAdvisor)
                .build();
        this.objectMapper = objectMapper;
        this.minioService = minioService;
        this.versionMapper = versionMapper;
        this.projectMapper = projectMapper;
        this.tika = new Tika();
        this.outputDir = outputDir;
        this.templatePath = templatePath.startsWith("classpath:") ?
                templatePath.substring(10) : templatePath;
        this.bucketName = bucketName;
    }

    /**
     * 生成课件（PPT + Word 教案），上传到 MinIO
     *
     * @param topic                课件主题（含用户表单信息的结构化文本）
     * @param slideCount           期望幻灯片页数
     * @param conversationSummary  AI 对话中提取的需求摘要
     * @param materialDescriptions 资料使用说明
     * @param files                用户上传的参考资料（可为 null）
     * @return Map 包含 pptObjectName 和 docObjectName
     */
    public Map<String, String> generateCourseware(String topic, int slideCount,
                                                   String conversationSummary,
                                                   String materialDescriptions,
                                                   MultipartFile[] files) {
        try {
            // 1. 构建综合检索查询（主题 + 对话摘要），提高 RAG 检索精度
            String searchQuery = topic;
            if (conversationSummary != null && !conversationSummary.isBlank()) {
                searchQuery = topic + " " + conversationSummary;
            }

            // 2. RAG 检索知识库
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(searchQuery)
                            .topK(8)
                            .similarityThreshold(0.6)
                            .build()
            );
            String ragContext = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));
            log.info("RAG 检索到 {} 篇相关文档片段，查询：{}", docs.size(), searchQuery);

            // 3. 提取用户上传文件的内容
            String fileContext = extractFileContents(files);

            // 4. 合并全部上下文
            StringBuilder fullContext = new StringBuilder();
            fullContext.append(ragContext);

            if (!fileContext.isEmpty()) {
                fullContext.append("\n---\n【用户上传的参考资料内容】\n").append(fileContext);
                log.info("已合并用户上传文件内容，共 {} 字符", fileContext.length());
            }

            if (materialDescriptions != null && !materialDescriptions.isBlank()) {
                fullContext.append("\n---\n【资料使用说明】\n").append(materialDescriptions);
            }

            // 5. 大模型生成大纲（传入对话摘要作为需求上下文）
            String prompt = buildOutlinePrompt(topic, slideCount, fullContext.toString(), conversationSummary);
            String aiResponse = chatClient.prompt().user(prompt).call().content();
            log.debug("AI 原始响应: {}", aiResponse);

            // 5. 解析大纲
            CoursewareOutline outline = parseOutline(aiResponse);
            if (outline.getSlides() == null || outline.getSlides().isEmpty()) {
                throw new RuntimeException("AI 生成的大纲内容为空");
            }

            // 6. 生成 PPT
            String pptPath = PptBuilder.createPptFromTemplate(outline, templatePath, outputDir);
            log.info("PPT 已生成: {}", pptPath);

            // 7. 生成 Word 教案
            String docPath = TeachingPlanBuilder.createTeachingPlan(outline, outputDir);
            log.info("Word 教案已生成: {}", docPath);

            // 8. 上传到 MinIO
            String uuid = UUID.randomUUID().toString();
            String pptObjectName = "courseware/" + uuid + ".pptx";
            String docObjectName = "courseware/" + uuid + "-教案.docx";

            minioService.uploadFile(bucketName, pptObjectName, pptPath);
            minioService.uploadFile(bucketName, docObjectName, docPath);
            log.info("已上传到 MinIO: {}, {}", pptObjectName, docObjectName);

            // 9. 删除本地临时文件
            new File(pptPath).delete();
            new File(docPath).delete();

            // 10. 返回结果
            Map<String, String> result = new HashMap<>();
            result.put("pptObjectName", pptObjectName);
            result.put("docObjectName", docObjectName);
            return result;

        } catch (Exception e) {
            log.error("课件生成失败", e);
            throw new RuntimeException("课件生成失败： " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Tika 从上传文件中提取文本内容
     */
    private String extractFileContents(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                String text = tika.parseToString(file.getInputStream());
                if (text != null && !text.isBlank()) {
                    sb.append("【").append(file.getOriginalFilename()).append("】\n");
                    // 限制每个文件最多 3000 字符，避免 prompt 过长
                    if (text.length() > 3000) {
                        sb.append(text, 0, 3000).append("...(已截断)");
                    } else {
                        sb.append(text);
                    }
                    sb.append("\n\n");
                    log.info("已提取文件内容: {} ({} 字符)", file.getOriginalFilename(), text.length());
                }
            } catch (IOException e) {
                log.warn("无法提取文件内容: {}", file.getOriginalFilename(), e);
            } catch (Exception e) {
                log.warn("Tika 解析文件失败: {}", file.getOriginalFilename(), e);
            }
        }
        return sb.toString();
    }

    /**
     * 从上传文件中提取内容，用于对话阶段的资料关联分析
     * 每个文件限制 2000 字符，避免 prompt 过长
     */
    private String extractFileContentForChat(MultipartFile[] files) {
        if (files == null || files.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                String text = tika.parseToString(file.getInputStream());
                if (text != null && !text.isBlank()) {
                    sb.append("【").append(file.getOriginalFilename()).append("】\n");
                    sb.append(text.length() > 2000 ? text.substring(0, 2000) + "..." : text);
                    sb.append("\n\n");
                }
            } catch (Exception e) {
                log.warn("对话阶段文件内容提取失败: {}", file.getOriginalFilename(), e);
            }
        }
        return sb.toString();
    }

    /**
     * 智能对话：教师自由输入，AI 主动追问并总结需求
     * 使用 JdbcChatMemoryRepository 按 conversationId 持久化对话历史
     * conversationId 格式：courseware_{userId}_{projectId}
     */
    public String chatForCourseware(String message, String conversationId,
                                     List<Map<String, String>> materials) {
        StringBuilder userPrompt = new StringBuilder();

        // 如果有上传的参考资料，附加到 prompt 中
        if (materials != null && !materials.isEmpty()) {
            userPrompt.append("教师上传了以下参考资料：\n");
            for (int i = 0; i < materials.size(); i++) {
                Map<String, String> mat = materials.get(i);
                userPrompt.append(String.format("%d. 文件名：%s", i + 1, mat.getOrDefault("name", "未知")));
                if (mat.get("description") != null && !mat.get("description").isBlank()) {
                    userPrompt.append(String.format("，教师说明：%s", mat.get("description")));
                }
                userPrompt.append("\n");
            }
            userPrompt.append("请在对话中参考这些资料的内容，主动分析每份资料与教学需求的关联，询问教师希望如何使用它们。\n\n");

            // 资料关联分析：针对每份资料从知识库检索相关内容
            StringBuilder materialContextBuilder = new StringBuilder();
            for (int i = 0; i < materials.size(); i++) {
                Map<String, String> mat = materials.get(i);
                String matName = mat.getOrDefault("name", "");
                String matDesc = mat.getOrDefault("description", "");
                String searchQuery = matName + " " + matDesc;
                try {
                    List<Document> matDocs = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(searchQuery)
                                    .topK(2)
                                    .similarityThreshold(0.6)
                                    .build());
                    if (matDocs != null && !matDocs.isEmpty()) {
                        String content = matDocs.stream()
                                .map(Document::getText)
                                .collect(Collectors.joining("\n"));
                        materialContextBuilder.append(String.format("【%s】关联内容：\n%s\n\n", matName, content));
                    }
                } catch (Exception e) {
                    log.warn("资料 RAG 检索失败: {}", matName, e);
                }
            }
            if (!materialContextBuilder.isEmpty()) {
                userPrompt.append("【资料关联知识库内容】\n以下是从知识库中检索到的与教师上传资料相关的内容，请结合分析资料与教学需求的关联：\n")
                        .append(materialContextBuilder).append("\n");
            }
        }

        // RAG：从本地知识库检索与当前对话相关的知识片段
        try {
            List<Document> ragDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(4)
                            .similarityThreshold(0.7)
                            .build());
            if (ragDocs != null && !ragDocs.isEmpty()) {
                String ragContext = ragDocs.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n---\n"));
                userPrompt.append("【知识库参考内容】\n以下是从本地知识库中检索到的与当前问题相关的资料，请在回答时参考：\n")
                        .append(ragContext)
                        .append("\n\n");
                log.info("课件对话 RAG 检索到 {} 条知识片段", ragDocs.size());
            }
        } catch (Exception e) {
            log.warn("课件对话 RAG 检索失败，跳过知识库增强: {}", e.getMessage());
        }

        userPrompt.append(message);

        try {
            // 通过 advisor context 传入 conversationId，实现按用户/项目隔离的记忆
            return coursewareChatClient.prompt()
                    .user(userPrompt.toString())
                    .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 对话失败, conversationId={}", conversationId, e);
            return "抱歉，AI 服务暂时不可用。请检查 Ollama 服务是否运行正常。错误：" + e.getMessage();
        }
    }

    /**
     * 获取下载链接
     */
    public String getDownloadUrl(String objectName) {
        try {
            return minioService.generatePresignedUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("生成下载链接失败: {}", objectName, e);
            throw new RuntimeException("生成下载链接失败", e);
        }
    }

    /**
     * 构造生成大纲的 Prompt
     */
    private String buildOutlinePrompt(String topic, int slideCount, String context, String conversationSummary) {
        String summarySection = "";
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            summarySection = "\n\n【教师详细需求】（来自对话交流）:\n" + conversationSummary;
        }

        return """
                你是一个资深课程设计师。请根据下面的【主题】、【教师详细需求】和【参考资料】，生成一份完整的教学课件大纲。

                要求：
                1. 总页数为 %d 页（不包含封面，封面系统自动添加）。
                2. 课件结构应包含：课程导入、核心知识点（分点讲解）、案例或实例、总结回顾。
                3. 严格遵循教师的需求描述，包括教学目标、重点难点、风格偏好、特殊要求等。
                4. 充分利用参考资料中的内容，将相关知识点融入课件。
                5. 每页包含：
                   - "page": 页码（从1开始）
                   - "title": 该页标题
                   - "points": 讲解要点（3~5条，简洁有力）
                   - "notes": 给讲师的备注（解释该页重点如何讲解）
                6. 课件整体输出一个 JSON，格式为：
                   {
                     "title": "完整课件标题",
                     "subtitle": "副标题（如适用）",
                     "slides": [ { "page":1, "title":"...", "points":["...","..."], "notes":"..." }, ... ]
                   }
                7. 只输出 JSON，不要包含任何其他文字。

                【主题】: %s%s

                【参考资料】:
                %s
                """.formatted(slideCount, topic, summarySection, context);
    }

    /**
     * 从 AI 响应中提取并解析 JSON
     */
    private CoursewareOutline parseOutline(String aiText) {
        String json = aiText.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json|```", "").trim();
        }
        try {
            return objectMapper.readValue(json, CoursewareOutline.class);
        } catch (JsonProcessingException e) {
            log.error("AI 返回的 JSON 解析失败，原始文本: {}", aiText);
            throw new RuntimeException("课件大纲 JSON 格式错误", e);
        }
    }

    // ================================================================
    //  AI 修改课件（核心迭代功能）
    // ================================================================

    /**
     * 根据教师的自然语言修改意见，调用大模型重新生成课件
     *
     * 流程：
     * 1. 将当前大纲 + 修改指令 + 上下文拼成 prompt
     * 2. 调用大模型获取修改后的大纲
     * 3. 用新大纲重新生成 PPT + Word
     * 4. 上传 MinIO
     * 5. 保存版本快照
     * 6. 更新项目的 objectName
     */
    public ModifyResult modifyCourseware(ModifyRequest request) {
        try {
            CoursewareOutline currentOutline = request.getCurrentOutline();
            String command = request.getCommand();
            String contextRef = request.getContextRef();

            // 1. 构造修改 prompt
            String outlineJson = objectMapper.writeValueAsString(currentOutline);
            String prompt = buildModifyPrompt(outlineJson, command, contextRef);

            // 2. 调用大模型
            log.info("调用大模型修改课件，指令：{}", command);
            String aiResponse = chatClient.prompt().user(prompt).call().content();
            log.debug("AI 修改响应: {}", aiResponse);

            // 3. 解析新大纲
            CoursewareOutline newOutline = parseOutline(aiResponse);
            if (newOutline.getSlides() == null || newOutline.getSlides().isEmpty()) {
                throw new RuntimeException("AI 修改后的大纲为空");
            }

            // 4. 重新生成 PPT + Word
            String pptPath = PptBuilder.createPptFromTemplate(newOutline, templatePath, outputDir);
            String docPath = TeachingPlanBuilder.createTeachingPlan(newOutline, outputDir);

            // 5. 上传到 MinIO
            String uuid = UUID.randomUUID().toString();
            String pptObjectName = "courseware/" + uuid + ".pptx";
            String docObjectName = "courseware/" + uuid + "-教案.docx";

            minioService.uploadFile(bucketName, pptObjectName, pptPath);
            minioService.uploadFile(bucketName, docObjectName, docPath);

            // 6. 删除本地临时文件
            new File(pptPath).delete();
            new File(docPath).delete();

            // 7. 保存版本快照
            String versionLabel = "";
            if (request.getProjectId() != null) {
                int versionCount = getVersionCount(request.getProjectId());
                versionLabel = "v" + (versionCount + 1) + " 修改";
                saveVersion(request.getProjectId(), versionLabel, newOutline, pptObjectName, docObjectName, command);

                // 8. 更新项目的 objectName
                Project update = new Project();
                update.setPptObjectName(pptObjectName);
                update.setDocObjectName(docObjectName);
                update.setStatus("done");
                update.setProgress(100);
                projectMapper.update(update,
                        new LambdaQueryWrapper<Project>().eq(Project::getId, request.getProjectId()));
            }

            // 9. 构造 AI 回复
            String aiReply = buildModifyReply(command, contextRef, currentOutline, newOutline);

            // 10. 返回结果
            ModifyResult result = new ModifyResult();
            result.setNewPptObjectName(pptObjectName);
            result.setNewPptDownloadUrl(getDownloadUrl(pptObjectName));
            result.setNewDocObjectName(docObjectName);
            result.setNewDocDownloadUrl(getDownloadUrl(docObjectName));
            result.setNewOutline(newOutline);
            result.setAiReply(aiReply);
            result.setVersionLabel(versionLabel);
            return result;

        } catch (Exception e) {
            log.error("课件修改失败", e);
            throw new RuntimeException("课件修改失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构造修改 prompt
     */
    private String buildModifyPrompt(String currentOutlineJson, String command, String contextRef) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个资深课件编辑助手。教师希望修改以下课件。\n\n");
        sb.append("【当前课件大纲】\n").append(currentOutlineJson).append("\n\n");
        sb.append("【教师修改要求】\n").append(command).append("\n\n");
        if (contextRef != null && !contextRef.isBlank()) {
            sb.append("【修改上下文】\n").append(contextRef).append("\n\n");
        }
        sb.append("请根据修改要求，返回修改后的完整课件大纲JSON。\n");
        sb.append("要求：\n");
        sb.append("1. 保持JSON格式与原大纲一致（title, subtitle, slides数组）\n");
        sb.append("2. 每个slide包含 page, title, points(数组), notes\n");
        sb.append("3. 如果是局部修改，只改动相关页面，其他页面保持不变\n");
        sb.append("4. 如果是全量重生成，返回完整新大纲\n");
        sb.append("5. 只输出JSON，不要包含其他文字\n");
        return sb.toString();
    }

    /**
     * 构造 AI 修改回复（前端展示用）
     */
    private String buildModifyReply(String command, String contextRef,
                                     CoursewareOutline oldOutline, CoursewareOutline newOutline) {
        int oldCount = oldOutline.getSlides() != null ? oldOutline.getSlides().size() : 0;
        int newCount = newOutline.getSlides() != null ? newOutline.getSlides().size() : 0;

        StringBuilder reply = new StringBuilder();
        reply.append("✅ 已根据您的要求修改课件。\n\n");

        if (contextRef != null && !contextRef.isBlank()) {
            reply.append("修改定位：").append(contextRef).append("\n");
        }
        reply.append("修改内容：").append(command).append("\n");
        reply.append("页面变化：").append(oldCount).append("页 → ").append(newCount).append("页\n");

        // 列出修改后的页面标题
        if (newOutline.getSlides() != null) {
            reply.append("\n修改后页面结构：\n");
            for (SlideData slide : newOutline.getSlides()) {
                reply.append("  ").append(slide.getPage()).append(". ").append(slide.getTitle()).append("\n");
            }
        }

        return reply.toString();
    }

    // ================================================================
    //  版本管理
    // ================================================================

    /** 保存版本快照 */
    public void saveVersion(Long projectId, String label, CoursewareOutline outline,
                             String pptObjectName, String docObjectName, String modifyCommand) {
        try {
            CoursewareVersion version = new CoursewareVersion();
            version.setProjectId(projectId);
            version.setVersionLabel(label);
            version.setOutlineJson(objectMapper.writeValueAsString(outline));
            version.setPptObjectName(pptObjectName);
            version.setDocObjectName(docObjectName);
            version.setModifyCommand(modifyCommand);
            versionMapper.insert(version);
            log.info("已保存版本快照：项目{}, 标签{}", projectId, label);
        } catch (JsonProcessingException e) {
            log.error("保存版本快照失败", e);
        }
    }

    /** 获取项目的版本列表 */
    public List<CoursewareVersion> listVersions(Long projectId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<CoursewareVersion>()
                        .eq(CoursewareVersion::getProjectId, projectId)
                        .orderByDesc(CoursewareVersion::getCreatedAt));
    }

    /** 回退到指定版本 */
    public Map<String, String> restoreVersion(Long versionId) {
        CoursewareVersion version = versionMapper.selectById(versionId);
        if (version == null) throw new RuntimeException("版本不存在");

        // 直接返回该版本的 objectName（前端用来更新预览）
        Map<String, String> result = new HashMap<>();
        result.put("pptObjectName", version.getPptObjectName());
        result.put("docObjectName", version.getDocObjectName());
        result.put("outlineJson", version.getOutlineJson());
        result.put("versionLabel", version.getVersionLabel());
        result.put("pptDownloadUrl", getDownloadUrl(version.getPptObjectName()));
        result.put("docDownloadUrl", getDownloadUrl(version.getDocObjectName()));

        // 更新项目的 objectName 指向这个版本
        if (version.getProjectId() != null) {
            Project update = new Project();
            update.setPptObjectName(version.getPptObjectName());
            update.setDocObjectName(version.getDocObjectName());
            projectMapper.update(update,
                    new LambdaQueryWrapper<Project>().eq(Project::getId, version.getProjectId()));
        }

        return result;
    }

    /** 获取项目的版本数量 */
    private int getVersionCount(Long projectId) {
        return Math.toIntExact(versionMapper.selectCount(
                new LambdaQueryWrapper<CoursewareVersion>()
                        .eq(CoursewareVersion::getProjectId, projectId)));
    }

    // ================================================================
    //  课件内容解析（预览用）
    // ================================================================

    /**
     * 从 MinIO 下载 PPTX 并解析出大纲结构
     */
    public CoursewareOutline parseOutlineFromMinio(String objectName) {
        try {
            // 1. 从 MinIO 下载到临时文件
            File tempFile = File.createTempFile("preview-", ".pptx");
            minioService.downloadFile(bucketName, objectName, tempFile.getAbsolutePath());

            // 2. 解析 PPTX
            CoursewareOutline outline = parseOutlineFromPptx(tempFile);

            // 3. 删除临时文件
            tempFile.delete();

            return outline;
        } catch (Exception e) {
            log.error("从 MinIO 解析课件失败: {}", objectName, e);
            throw new RuntimeException("解析课件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从 PPTX 文件解析出大纲
     */
    private CoursewareOutline parseOutlineFromPptx(File pptxFile) throws IOException {
        CoursewareOutline outline = new CoursewareOutline();
        List<SlideData> slides = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(pptxFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            List<XSLFSlide> slideList = ppt.getSlides();
            for (int i = 0; i < slideList.size(); i++) {
                XSLFSlide slide = slideList.get(i);
                SlideData slideData = new SlideData();
                slideData.setPage(i + 1);

                // 提取文本内容
                List<String> texts = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            texts.add(text.trim());
                        }
                    }
                }

                if (!texts.isEmpty()) {
                    // 第一行作为标题，其余作为要点
                    slideData.setTitle(texts.get(0));
                    if (texts.size() > 1) {
                        slideData.setPoints(texts.subList(1, texts.size()));
                    } else {
                        slideData.setPoints(List.of());
                    }
                } else {
                    slideData.setTitle("第" + (i + 1) + "页");
                    slideData.setPoints(List.of());
                }

                slideData.setNotes("");
                slides.add(slideData);
            }
        }

        // 第一页通常是封面
        if (!slides.isEmpty()) {
            outline.setTitle(slides.get(0).getTitle());
            slides.remove(0); // 移除封面，只保留内容页
            // 重新编号
            for (int i = 0; i < slides.size(); i++) {
                slides.get(i).setPage(i + 1);
            }
        }

        outline.setSlides(slides);
        return outline;
    }

    // ================================================================
    //  保存初始版本（生成时调用）
    // ================================================================

    /**
     * 生成课件后，自动保存初始版本
     */
    public void saveInitialVersion(Long projectId, CoursewareOutline outline,
                                    String pptObjectName, String docObjectName) {
        saveVersion(projectId, "v1 初稿", outline, pptObjectName, docObjectName, "初始生成");
    }
}
