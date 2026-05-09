package com.example.aiteacher.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String subject;

    private String grade;

    private String hours;

    private String topic;

    private String goal;

    private String keyPoint;

    private String difficulty;

    private String style;

    private String extras;

    private Integer slideCount;

    private String status;

    private Integer progress;

    private String pptObjectName;

    private String docObjectName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
