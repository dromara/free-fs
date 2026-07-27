-- File collection (anonymous reverse-share upload link)

CREATE TABLE file_collections (
  id varchar(128) PRIMARY KEY,
  user_id varchar(128) NOT NULL,
  workspace_id varchar(128) NOT NULL,
  target_folder_id varchar(128) NOT NULL,
  storage_platform_setting_id varchar(128),
  collection_name varchar(255) NOT NULL,
  description varchar(1000),
  access_code_hash varchar(255),
  expire_time timestamp,
  max_file_size bigint NOT NULL DEFAULT 1073741824,
  allowed_extensions varchar(1000),
  status varchar(20) NOT NULL DEFAULT 'OPEN',
  submission_count integer NOT NULL DEFAULT 0,
  file_count integer NOT NULL DEFAULT 0,
  total_size bigint NOT NULL DEFAULT 0,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_collection_workspace_status
  ON file_collections (workspace_id, status, created_at);
CREATE INDEX idx_collection_target_folder
  ON file_collections (target_folder_id);
CREATE INDEX idx_collection_user
  ON file_collections (user_id);

CREATE TABLE file_collection_submissions (
  id varchar(128) PRIMARY KEY,
  collection_id varchar(128) NOT NULL,
  submitter_name varchar(64) NOT NULL,
  submitter_ip varchar(50),
  user_agent varchar(512),
  folder_id varchar(128) NOT NULL,
  upload_token_hash varchar(64) NOT NULL,
  file_count integer NOT NULL DEFAULT 0,
  total_size bigint NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL DEFAULT 'UPLOADING',
  completed_at timestamp,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_submission_collection_time
  ON file_collection_submissions (collection_id, created_at);
CREATE INDEX idx_submission_folder
  ON file_collection_submissions (folder_id);

ALTER TABLE file_transfer_task
  ADD COLUMN collection_id varchar(128),
  ADD COLUMN collection_submission_id varchar(128);

CREATE INDEX idx_transfer_collection_submission
  ON file_transfer_task (collection_submission_id);
CREATE INDEX idx_transfer_collection
  ON file_transfer_task (collection_id);
