CREATE INDEX IF NOT EXISTS "idx_file_content_dedup"
  ON "file_info" ("storage_platform_setting_id", "content_md5", "size", "is_dir");

CREATE INDEX IF NOT EXISTS "idx_file_object_reference"
  ON "file_info" ("storage_platform_setting_id", "object_key");
