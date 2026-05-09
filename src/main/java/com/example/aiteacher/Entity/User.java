package com.example.aiteacher.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private Long id;

    /** 登录账号（工号或自定义） */
    private String username;

    /** 加密后的密码 */
    private String password;

    /** 教师姓名 */
    private String teacherName;

    /** 所属学科 */
    private String subject;

    /** 任教年级（多个年级逗号分隔） */
    private String grade;

    /** 职称 */
    private String title;

    /** 电子邮箱 */
    private String email;

    /** 联系电话（存储完整号码，返回前端时脱敏） */
    private String phone;

    private String avatarUrl;

    /** 个人简介 */
    private String bio;

    /** 账号状态（1启用 0禁用） */
    private Integer status = 1;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    protected void onCreate() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

}
