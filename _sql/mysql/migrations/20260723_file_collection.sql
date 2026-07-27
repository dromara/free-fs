-- File collection (anonymous reverse-share upload link)
SET NAMES utf8mb4;

CREATE TABLE `file_collections` (
  `id` varchar(128) NOT NULL COMMENT '收集ID',
  `user_id` varchar(128) NOT NULL COMMENT '创建人ID',
  `workspace_id` varchar(128) NOT NULL COMMENT '所属工作空间ID',
  `target_folder_id` varchar(128) NOT NULL COMMENT '收集目标文件夹ID',
  `storage_platform_setting_id` varchar(128) DEFAULT NULL COMMENT '存储平台配置ID，空表示本地存储',
  `collection_name` varchar(255) NOT NULL COMMENT '收集名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '收集说明',
  `access_code_hash` varchar(255) DEFAULT NULL COMMENT '访问码哈希',
  `expire_time` datetime DEFAULT NULL COMMENT '截止时间，空表示永久',
  `max_file_size` bigint NOT NULL DEFAULT 1073741824 COMMENT '单文件大小限制（字节）',
  `allowed_extensions` varchar(1000) DEFAULT NULL COMMENT '允许扩展名，逗号分隔',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  `submission_count` int NOT NULL DEFAULT 0 COMMENT '提交会话数',
  `file_count` int NOT NULL DEFAULT 0 COMMENT '成功上传文件数',
  `total_size` bigint NOT NULL DEFAULT 0 COMMENT '成功上传总字节数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_collection_workspace_status` (`workspace_id`, `status`, `created_at`),
  KEY `idx_collection_target_folder` (`target_folder_id`),
  KEY `idx_collection_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件收集';

CREATE TABLE `file_collection_submissions` (
  `id` varchar(128) NOT NULL COMMENT '提交记录ID',
  `collection_id` varchar(128) NOT NULL COMMENT '收集ID',
  `submitter_name` varchar(64) NOT NULL COMMENT '提交人姓名',
  `submitter_ip` varchar(50) DEFAULT NULL COMMENT '提交人IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `folder_id` varchar(128) NOT NULL COMMENT '自动创建的提交文件夹ID',
  `upload_token_hash` varchar(64) NOT NULL COMMENT '一次性上传令牌SHA-256',
  `file_count` int NOT NULL DEFAULT 0 COMMENT '成功上传文件数',
  `total_size` bigint NOT NULL DEFAULT 0 COMMENT '成功上传总字节数',
  `status` varchar(20) NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING/COMPLETED',
  `completed_at` datetime DEFAULT NULL COMMENT '完成提交时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_submission_collection_time` (`collection_id`, `created_at`),
  KEY `idx_submission_folder` (`folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件收集提交记录';

ALTER TABLE `file_transfer_task`
  ADD COLUMN `collection_id` varchar(128) DEFAULT NULL COMMENT '文件收集ID（普通上传为空）' AFTER `workspace_id`,
  ADD COLUMN `collection_submission_id` varchar(128) DEFAULT NULL COMMENT '文件收集提交记录ID（普通上传为空）' AFTER `collection_id`,
  ADD KEY `idx_collection_submission` (`collection_submission_id`),
  ADD KEY `idx_collection_id` (`collection_id`);
