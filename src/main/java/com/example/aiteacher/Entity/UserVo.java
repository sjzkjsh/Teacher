package com.example.aiteacher.Entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UserVo {

    @Schema(description = "用户姓名")
    private String name;

    @Schema(description = "用户头像")
    private String avatarUrl;
}
