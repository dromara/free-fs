ALTER TABLE `file_info`
  MODIFY COLUMN `content_md5` varchar(64) NULL DEFAULT NULL COMMENT '用于秒传和文件校验';

CREATE INDEX `idx_file_content_dedup`
  ON `file_info` (`storage_platform_setting_id`, `content_md5`, `size`, `is_dir`);

CREATE INDEX `idx_file_object_reference`
  ON `file_info` (`storage_platform_setting_id`, `object_key`);
