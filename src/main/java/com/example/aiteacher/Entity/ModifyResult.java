package com.example.aiteacher.Entity;

import lombok.Data;

@Data
public class ModifyResult {

    private String newPptObjectName;
    private String newPptDownloadUrl;
    private String newDocObjectName;
    private String newDocDownloadUrl;

    /** 修改后的新大纲 */
    private CoursewareOutline newOutline;

    /** AI 的回复说明 */
    private String aiReply;

    /** 新版本标签 */
    private String versionLabel;
}
