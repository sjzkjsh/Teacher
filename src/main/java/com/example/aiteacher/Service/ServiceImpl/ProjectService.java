package com.example.aiteacher.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.Entity.ChatMessage;
import com.example.aiteacher.Entity.Project;
import com.example.aiteacher.Mapper.ChatMessageMapper;
import com.example.aiteacher.Mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ProjectService(ProjectMapper projectMapper, ChatMessageMapper chatMessageMapper) {
        this.projectMapper = projectMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    // ========== 项目 CRUD ==========

    /** 创建项目 */
    public Project createProject(Project project) {
        if (project.getStatus() == null) project.setStatus("draft");
        if (project.getProgress() == null) project.setProgress(0);
        if (project.getSlideCount() == null) project.setSlideCount(10);
        if (project.getStyle() == null) project.setStyle("rigorous");
        projectMapper.insert(project);
        return project;
    }

    /**
     * 获取用户的项目列表（支持多条件筛选）
     *
     * @param userId    用户ID
     * @param keyword   搜索关键词（匹配标题、主题）
     * @param subject   学科筛选
     * @param status    状态筛选（draft/done/exported）
     * @param timeRange 时间范围：today/week/month/null
     * @param hasPpt    是否筛选有PPT的项目
     * @param hasDoc    是否筛选有教案的项目
     * @param sortBy    排序字段：updated/created/title
     * @param sortDir   排序方向：asc/desc
     */
    public List<Project> listProjects(Long userId, String keyword, String subject,
                                       String status, String timeRange,
                                       Boolean hasPpt, Boolean hasDoc,
                                       String sortBy, String sortDir) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getUserId, userId);

        // 关键词搜索（匹配标题、主题、学科）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(Project::getTitle, keyword)
                    .or().like(Project::getTopic, keyword)
                    .or().like(Project::getSubject, keyword));
        }

        // 学科筛选
        if (subject != null && !subject.isBlank() && !"全部".equals(subject)) {
            wrapper.eq(Project::getSubject, subject);
        }

        // 状态筛选
        if (status != null && !status.isBlank() && !"all".equals(status)) {
            wrapper.eq(Project::getStatus, status);
        }

        // 时间范围筛选（优先用 updatedAt，兼容旧数据 createdAt 为 null 的情况）
        if (timeRange != null && !timeRange.isBlank()) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = switch (timeRange) {
                case "today" -> now.toLocalDate().atStartOfDay();
                case "week" -> now.minusDays(7);
                case "month" -> now.minusDays(30);
                default -> null;
            };
            if (cutoff != null) {
                LocalDateTime finalCutoff = cutoff;
                wrapper.and(w -> w
                        .ge(Project::getUpdatedAt, finalCutoff)
                        .or().ge(Project::getCreatedAt, finalCutoff));
            }
        }

        // 文件类型筛选
        if (Boolean.TRUE.equals(hasPpt)) {
            wrapper.isNotNull(Project::getPptObjectName).and(w -> w.ne(Project::getPptObjectName, ""));
        }
        if (Boolean.TRUE.equals(hasDoc)) {
            wrapper.isNotNull(Project::getDocObjectName).and(w -> w.ne(Project::getDocObjectName, ""));
        }

        // 排序
        boolean isAsc = "asc".equals(sortDir);
        if ("created".equals(sortBy)) {
            wrapper.orderBy(true, isAsc, Project::getCreatedAt);
        } else if ("title".equals(sortBy)) {
            wrapper.orderBy(true, isAsc, Project::getTitle);
        } else {
            wrapper.orderBy(true, isAsc, Project::getUpdatedAt);
        }

        return projectMapper.selectList(wrapper);
    }

    /** 获取单个项目 */
    public Project getProject(Long id) {
        return projectMapper.selectById(id);
    }

    /** 更新项目 */
    public Project updateProject(Long id, Project update) {
        Project existing = projectMapper.selectById(id);
        if (existing == null) throw new RuntimeException("项目不存在");

        if (update.getTitle() != null) existing.setTitle(update.getTitle());
        if (update.getSubject() != null) existing.setSubject(update.getSubject());
        if (update.getGrade() != null) existing.setGrade(update.getGrade());
        if (update.getHours() != null) existing.setHours(update.getHours());
        if (update.getTopic() != null) existing.setTopic(update.getTopic());
        if (update.getGoal() != null) existing.setGoal(update.getGoal());
        if (update.getKeyPoint() != null) existing.setKeyPoint(update.getKeyPoint());
        if (update.getDifficulty() != null) existing.setDifficulty(update.getDifficulty());
        if (update.getStyle() != null) existing.setStyle(update.getStyle());
        if (update.getExtras() != null) existing.setExtras(update.getExtras());
        if (update.getSlideCount() != null) existing.setSlideCount(update.getSlideCount());
        if (update.getStatus() != null) existing.setStatus(update.getStatus());
        if (update.getProgress() != null) existing.setProgress(update.getProgress());
        if (update.getPptObjectName() != null) existing.setPptObjectName(update.getPptObjectName());
        if (update.getDocObjectName() != null) existing.setDocObjectName(update.getDocObjectName());

        projectMapper.updateById(existing);
        return existing;
    }

    /** 删除项目及其对话记录 */
    public void deleteProject(Long id) {
        // 先删对话
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getProjectId, id)
        );
        // 再删项目
        projectMapper.deleteById(id);
    }

    // ========== 对话消息 ==========

    /** 保存一条消息 */
    public ChatMessage saveMessage(ChatMessage msg) {
        chatMessageMapper.insert(msg);
        return msg;
    }

    /** 获取项目的所有对话 */
    public List<ChatMessage> getMessages(Long projectId) {
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getProjectId, projectId)
                        .orderByAsc(ChatMessage::getCreatedAt)
        );
    }

    /** 获取项目统计信息（只统计当前用户） */
    public Map<String, Object> getStats(Long userId) {
        // 用 MyBatis Plus 直接做 COUNT，不查全量数据
        long total = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getUserId, userId));
        long done = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getUserId, userId)
                        .in(Project::getStatus, "done", "exported"));
        long inProgress = total - done;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("done", done);
        stats.put("inProgress", inProgress);
        return stats;
    }
}
