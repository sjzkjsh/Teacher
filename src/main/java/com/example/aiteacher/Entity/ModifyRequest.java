package com.example.aiteacher.Entity;

import lombok.Data;

@Data
public class ModifyRequest {

    /** 项目ID（用于版本管理） */
    private Long projectId;

    /** 当前课件在MinIO的key */
    private String currentObjectName;

    /** 用户的修改指令，如"第3页增加一个案例" */
    private String command;

    /** 上下文定位，如"@第3页「概念引入」" */
    private String contextRef;

    /** 当前大纲（前端传过来的） */
    private CoursewareOutline currentOutline;
}
