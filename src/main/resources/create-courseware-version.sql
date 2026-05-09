-- 课件版本表（手动执行）
CREATE TABLE IF NOT EXISTS `courseware_version` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `version_label` VARCHAR(50) DEFAULT '' COMMENT '版本标签，如 v1 初稿',
    `outline_json` MEDIUMTEXT COMMENT '完整大纲JSON',
    `ppt_object_name` VARCHAR(500) DEFAULT '' COMMENT 'MinIO中PPT对象名',
    `doc_object_name` VARCHAR(500) DEFAULT '' COMMENT 'MinIO中教案对象名',
    `modify_command` VARCHAR(500) DEFAULT '' COMMENT '触发本次修改的指令',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课件版本';
