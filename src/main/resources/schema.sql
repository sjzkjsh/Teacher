-- 备课项目表
CREATE TABLE IF NOT EXISTS `project` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '项目标题',
    `subject` VARCHAR(50) DEFAULT '' COMMENT '学科',
    `grade` VARCHAR(20) DEFAULT '' COMMENT '年级',
    `hours` VARCHAR(50) DEFAULT '' COMMENT '课时安排',
    `topic` VARCHAR(500) DEFAULT '' COMMENT '教学主题描述',
    `goal` TEXT COMMENT '核心教学目标',
    `key_point` TEXT COMMENT '教学重点',
    `difficulty` TEXT COMMENT '教学难点',
    `style` VARCHAR(20) DEFAULT 'rigorous' COMMENT '风格：rigorous/lively',
    `extras` VARCHAR(500) DEFAULT '' COMMENT '特殊要求，逗号分隔',
    `slide_count` INT DEFAULT 10 COMMENT 'PPT页数',
    `status` VARCHAR(20) DEFAULT 'draft' COMMENT '状态：draft/generating/done/exported',
    `progress` INT DEFAULT 0 COMMENT '进度百分比',
    `ppt_object_name` VARCHAR(500) DEFAULT '' COMMENT 'MinIO中PPT对象名',
    `doc_object_name` VARCHAR(500) DEFAULT '' COMMENT 'MinIO中教案对象名',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备课项目';

-- 对话消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `role` VARCHAR(10) NOT NULL COMMENT '角色：ai/user',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `msg_type` VARCHAR(20) DEFAULT 'text' COMMENT '消息类型：text/clarify/confirm',
    `extra_data` JSON DEFAULT NULL COMMENT '附加数据(选项、确认数据等JSON)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息';

-- Spring AI 对话记忆表（持久化 AI 对话历史，按 conversation_id 隔离不同用户/项目的对话）
CREATE TABLE IF NOT EXISTS `SPRING_AI_CHAT_MEMORY` (
    `id` VARCHAR(255) NOT NULL DEFAULT (UUID()),
    `conversation_id` VARCHAR(255) NOT NULL,
    `content` TEXT,
    `type` VARCHAR(100),
    `timestamp` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记忆';

-- 课件版本表
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
