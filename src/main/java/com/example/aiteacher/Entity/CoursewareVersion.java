package com.example.aiteacher.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("courseware_version")
public class CoursewareVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String versionLabel;

    /** 完整大纲JSON */
    private String outlineJson;

    private String pptObjectName;

    private String docObjectName;

    /** 触发本次修改的指令 */
    private String modifyCommand;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
