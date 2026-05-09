package com.example.aiteacher.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String role;

    private String content;

    private String msgType;

    /** JSON格式的附加数据 */
    private String extraData;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
