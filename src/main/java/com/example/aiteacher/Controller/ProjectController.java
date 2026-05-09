package com.example.aiteacher.Controller;

import com.example.aiteacher.Entity.ChatMessage;
import com.example.aiteacher.Entity.Project;
import com.example.aiteacher.Entity.Result;
import com.example.aiteacher.Service.ServiceImpl.ProjectService;
import com.example.aiteacher.Util.LoginUserHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 创建备课项目
     */
    @PostMapping("/create")
    public Result<Project> create(@RequestBody Project project) {
        project.setUserId(LoginUserHolder.getLoginUser().getUserId());
        Project created = projectService.createProject(project);
        return Result.success(created);
    }

    /**
     * 获取项目列表（支持多条件筛选）
     */
    @GetMapping("/list")
    public Result<List<Project>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) Boolean hasPpt,
            @RequestParam(required = false) Boolean hasDoc,
            @RequestParam(required = false, defaultValue = "updated") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        Long userId = LoginUserHolder.getLoginUser().getUserId();
        List<Project> projects = projectService.listProjects(
                userId, keyword, subject, status, timeRange, hasPpt, hasDoc, sortBy, sortDir);
        return Result.success(projects);
    }

    /**
     * 获取单个项目详情
     */
    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        Project project = projectService.getProject(id);
        if (project == null) return Result.error("项目不存在");
        return Result.success(project);
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        Project updated = projectService.updateProject(id, project);
        return Result.success(updated);
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success("删除成功");
    }

    /**
     * 保存对话消息
     */
    @PostMapping("/{projectId}/messages")
    public Result<ChatMessage> saveMessage(
            @PathVariable Long projectId,
            @RequestBody ChatMessage message) {
        message.setProjectId(projectId);
        ChatMessage saved = projectService.saveMessage(message);
        return Result.success(saved);
    }

    /**
     * 获取项目的所有对话消息
     */
    @GetMapping("/{projectId}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long projectId) {
        List<ChatMessage> messages = projectService.getMessages(projectId);
        return Result.success(messages);
    }

    /**
     * 获取用户统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long userId = LoginUserHolder.getLoginUser().getUserId();
        Map<String, Object> stats = projectService.getStats(userId);
        return Result.success(stats);
    }
}
