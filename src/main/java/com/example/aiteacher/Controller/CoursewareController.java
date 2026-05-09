package com.example.aiteacher.Controller;

import com.example.aiteacher.Entity.*;
import com.example.aiteacher.Service.ServiceImpl.CoursewareService;
import com.example.aiteacher.Util.LoginUserHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/courseware")
public class CoursewareController {

    private final CoursewareService coursewareService;

    public CoursewareController(CoursewareService coursewareService) {
        this.coursewareService = coursewareService;
    }

    // ================================================================
    //  生成课件
    // ================================================================

    /**
     * 生成课件（PPT + Word 教案）
     */
    @PostMapping("/generate")
    public Result<Map<String, String>> generate(
            @RequestParam String topic,
            @RequestParam(defaultValue = "10") int slideCount,
            @RequestParam(value = "conversationSummary", required = false) String conversationSummary,
            @RequestParam(value = "materialDescriptions", required = false) String materialDescriptions,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        Map<String, String> objectNames = coursewareService.generateCourseware(
                topic, slideCount, conversationSummary, materialDescriptions, files);

        String pptDownloadUrl = coursewareService.getDownloadUrl(objectNames.get("pptObjectName"));
        String docDownloadUrl = coursewareService.getDownloadUrl(objectNames.get("docObjectName"));

        Map<String, String> data = new HashMap<>();
        data.put("pptObjectName", objectNames.get("pptObjectName"));
        data.put("pptDownloadUrl", pptDownloadUrl);
        data.put("docObjectName", objectNames.get("docObjectName"));
        data.put("docDownloadUrl", docDownloadUrl);
        return Result.success(data);
    }

    // ================================================================
    //  智能对话（需求澄清）
    // ================================================================

    /**
     * 智能对话接口：教师自由输入，AI 主动追问并总结需求
     * 按 userId + projectId 隔离对话记忆，持久化到数据库
     */
    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        Long projectId = body.get("projectId") != null ?
                Long.valueOf(body.get("projectId").toString()) : 0L;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> materials = (List<Map<String, String>>) body.get("materials");

        Long userId = LoginUserHolder.getLoginUser().getUserId();
        // conversationId 格式：courseware_{userId}_{projectId}，实现记忆隔离
        String conversationId = "courseware_" + userId + "_" + projectId;

        String reply = coursewareService.chatForCourseware(message, conversationId, materials);
        return Result.success(reply);
    }

    // ================================================================
    //  课件内容解析（预览用）
    // ================================================================

    /**
     * 获取课件大纲内容（从 MinIO 下载 PPT 并解析）
     * 前端预览页面用此接口获取真实的课件结构
     */
    @GetMapping("/detail")
    public Result<CoursewareOutline> detail(@RequestParam String objectName) {
        CoursewareOutline outline = coursewareService.parseOutlineFromMinio(objectName);
        return Result.success(outline);
    }

    // ================================================================
    //  AI 修改课件（迭代优化核心）
    // ================================================================

    /**
     * 根据教师的自然语言修改意见，调用大模型重新生成课件
     *
     * 流程：当前大纲 + 修改指令 → 大模型生成新大纲 → 重新生成PPT+Word → 保存版本
     */
    @PostMapping("/modify")
    public Result<ModifyResult> modify(@RequestBody ModifyRequest request) {
        ModifyResult result = coursewareService.modifyCourseware(request);
        return Result.success(result);
    }

    // ================================================================
    //  版本管理
    // ================================================================

    /**
     * 获取项目的版本列表
     */
    @GetMapping("/version/list")
    public Result<List<CoursewareVersion>> listVersions(@RequestParam Long projectId) {
        List<CoursewareVersion> versions = coursewareService.listVersions(projectId);
        return Result.success(versions);
    }

    /**
     * 回退到指定版本
     * 返回该版本的 objectName 和下载链接
     */
    @PostMapping("/version/restore")
    public Result<Map<String, String>> restoreVersion(@RequestParam Long versionId) {
        Map<String, String> result = coursewareService.restoreVersion(versionId);
        return Result.success(result);
    }

    // ================================================================
    //  下载链接
    // ================================================================

    @GetMapping("/download/ppt")
    public Result<String> downloadPpt(@RequestParam String objectName) {
        return Result.success(coursewareService.getDownloadUrl(objectName));
    }

    @GetMapping("/download/doc")
    public Result<String> downloadDoc(@RequestParam String objectName) {
        return Result.success(coursewareService.getDownloadUrl(objectName));
    }
}
