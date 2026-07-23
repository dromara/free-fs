-- ==============================================================
-- Target Server Type    : PostgreSQL
-- Target Server Version : 14/15/16+
-- File Encoding         : 65001
-- Description           : free-fs 全量数据库脚本
-- ==============================================================

SET session_replication_role = 'replica';

-- 1. file_info
DROP TABLE IF EXISTS "file_info";
CREATE TABLE "file_info" (
                             "id" varchar(128) NOT NULL,
                             "object_key" varchar(128) DEFAULT NULL,
                             "original_name" varchar(128) NOT NULL,
                             "display_name" varchar(128) NOT NULL,
                             "suffix" varchar(20) DEFAULT NULL,
                             "size" bigint DEFAULT NULL,
                             "mime_type" varchar(128) DEFAULT NULL,
                             "is_dir" smallint NOT NULL,
                             "parent_id" varchar(128) DEFAULT NULL,
                             "workspace_id" varchar(128) NOT NULL,
                             "user_id" varchar(128) NOT NULL,
                             "content_md5" text,
                             "storage_platform_setting_id" varchar(128) DEFAULT NULL,
                             "upload_time" timestamp NOT NULL,
                             "update_time" timestamp DEFAULT NULL,
                             "last_access_time" timestamp DEFAULT NULL,
                             "is_deleted" smallint DEFAULT NULL,
                             "deleted_time" timestamp DEFAULT NULL,
                             PRIMARY KEY ("id")
);
CREATE INDEX "idx_workspace_query" ON "file_info" ("workspace_id", "user_id", "is_deleted", "parent_id");
CREATE INDEX "idx_file_content_dedup" ON "file_info" ("storage_platform_setting_id", "content_md5", "size", "is_dir");
CREATE INDEX "idx_file_object_reference" ON "file_info" ("storage_platform_setting_id", "object_key");

-- 2. file_share_access_record
DROP TABLE IF EXISTS "file_share_access_record";
CREATE TABLE "file_share_access_record" (
                                            "id" BIGSERIAL PRIMARY KEY,
                                            "share_id" varchar(128) NOT NULL,
                                            "access_ip" varchar(50) DEFAULT NULL,
                                            "access_address" varchar(255) DEFAULT NULL,
                                            "browser" varchar(255) DEFAULT NULL,
                                            "os" varchar(512) DEFAULT NULL,
                                            "access_time" timestamp NOT NULL
);

-- 3. file_share_items
DROP TABLE IF EXISTS "file_share_items";
CREATE TABLE "file_share_items" (
                                    "share_id" varchar(128) NOT NULL,
                                    "file_id" varchar(128) NOT NULL,
                                    "created_at" timestamp NOT NULL,
                                    PRIMARY KEY ("share_id", "file_id")
);

-- 4. file_shares
DROP TABLE IF EXISTS "file_shares";
CREATE TABLE "file_shares" (
                               "id" varchar(128) NOT NULL,
                               "user_id" varchar(128) NOT NULL,
                               "workspace_id" varchar(128) NOT NULL,
                               "share_name" varchar(255) NOT NULL,
                               "share_code" varchar(6) DEFAULT NULL,
                               "expire_time" timestamp DEFAULT NULL,
                               "scope" varchar(255) NOT NULL,
                               "view_count" int DEFAULT 0,
                               "max_view_count" int DEFAULT NULL,
                               "download_count" int DEFAULT 0,
                               "max_download_count" int DEFAULT NULL,
                               "created_at" timestamp NOT NULL,
                               "updated_at" timestamp NOT NULL,
                               PRIMARY KEY ("id")
);

-- 4a. file_collections
DROP TABLE IF EXISTS "file_collections";
CREATE TABLE "file_collections" (
                                      "id" varchar(128) NOT NULL,
                                      "user_id" varchar(128) NOT NULL,
                                      "workspace_id" varchar(128) NOT NULL,
                                      "target_folder_id" varchar(128) NOT NULL,
                                      "storage_platform_setting_id" varchar(128) DEFAULT NULL,
                                      "collection_name" varchar(255) NOT NULL,
                                      "description" varchar(1000) DEFAULT NULL,
                                      "access_code_hash" varchar(255) DEFAULT NULL,
                                      "expire_time" timestamp DEFAULT NULL,
                                      "max_file_size" bigint NOT NULL DEFAULT 1073741824,
                                      "allowed_extensions" varchar(1000) DEFAULT NULL,
                                      "status" varchar(20) NOT NULL DEFAULT 'OPEN',
                                      "submission_count" int NOT NULL DEFAULT 0,
                                      "file_count" int NOT NULL DEFAULT 0,
                                      "total_size" bigint NOT NULL DEFAULT 0,
                                      "created_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      "updated_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY ("id")
);
CREATE INDEX "idx_collection_workspace_status" ON "file_collections" ("workspace_id", "status", "created_at");
CREATE INDEX "idx_collection_target_folder" ON "file_collections" ("target_folder_id");
CREATE INDEX "idx_collection_user" ON "file_collections" ("user_id");

-- 4b. file_collection_submissions
DROP TABLE IF EXISTS "file_collection_submissions";
CREATE TABLE "file_collection_submissions" (
                                                 "id" varchar(128) NOT NULL,
                                                 "collection_id" varchar(128) NOT NULL,
                                                 "submitter_name" varchar(64) NOT NULL,
                                                 "submitter_ip" varchar(50) DEFAULT NULL,
                                                 "user_agent" varchar(512) DEFAULT NULL,
                                                 "folder_id" varchar(128) NOT NULL,
                                                 "upload_token_hash" varchar(64) NOT NULL,
                                                 "file_count" int NOT NULL DEFAULT 0,
                                                 "total_size" bigint NOT NULL DEFAULT 0,
                                                 "status" varchar(20) NOT NULL DEFAULT 'UPLOADING',
                                                 "completed_at" timestamp DEFAULT NULL,
                                                 "created_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 "updated_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 PRIMARY KEY ("id")
);
CREATE INDEX "idx_submission_collection_time" ON "file_collection_submissions" ("collection_id", "created_at");
CREATE INDEX "idx_submission_folder" ON "file_collection_submissions" ("folder_id");

-- 5. file_transfer_task
DROP TABLE IF EXISTS "file_transfer_task";
CREATE TABLE "file_transfer_task" (
                                      "id" BIGSERIAL PRIMARY KEY,
                                      "task_id" varchar(64) NOT NULL,
                                      "upload_id" varchar(255) DEFAULT NULL,
                                      "parent_id" varchar(128) DEFAULT NULL,
                                      "user_id" varchar(128) NOT NULL,
                                      "workspace_id" varchar(128) NOT NULL,
                                      "collection_id" varchar(128) DEFAULT NULL,
                                      "collection_submission_id" varchar(128) DEFAULT NULL,
                                      "storage_platform_setting_id" varchar(255) DEFAULT NULL,
                                      "object_key" varchar(255) NOT NULL,
                                      "file_id" varchar(128) DEFAULT NULL,
                                      "file_name" varchar(255) NOT NULL,
                                      "file_size" bigint NOT NULL,
                                      "file_md5" varchar(64) DEFAULT NULL,
                                      "suffix" varchar(50) NOT NULL,
                                      "mime_type" varchar(255) NOT NULL,
                                      "total_chunks" int NOT NULL,
                                      "task_type" varchar(32) DEFAULT NULL,
                                      "uploaded_chunks" int DEFAULT 0,
                                      "chunk_size" bigint DEFAULT 5242880,
                                      "uploaded_size" bigint DEFAULT 0,
                                      "status" varchar(20) NOT NULL DEFAULT 'uploading',
                                      "error_msg" varchar(500) DEFAULT NULL,
                                      "start_time" timestamp NOT NULL,
                                      "complete_time" timestamp DEFAULT NULL,
                                      "created_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      "updated_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      CONSTRAINT "uk_task_id" UNIQUE ("task_id")
);
CREATE INDEX "idx_transfer_collection_submission" ON "file_transfer_task" ("collection_submission_id");
CREATE INDEX "idx_transfer_collection" ON "file_transfer_task" ("collection_id");

-- 6. file_user_favorites
DROP TABLE IF EXISTS "file_user_favorites";
CREATE TABLE "file_user_favorites" (
                                       "user_id" varchar(128) NOT NULL,
                                       "workspace_id" varchar(128) NOT NULL,
                                       "file_id" varchar(128) NOT NULL,
                                       "favorite_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       PRIMARY KEY ("workspace_id", "user_id", "file_id")
);

-- 7. storage_platform
DROP TABLE IF EXISTS "storage_platform";
CREATE TABLE "storage_platform" (
                                    "id" SERIAL PRIMARY KEY,
                                    "name" varchar(255) NOT NULL,
                                    "identifier" varchar(128) NOT NULL,
                                    "config_scheme" json NOT NULL,
                                    "icon" varchar(128) DEFAULT NULL,
                                    "link" varchar(255) DEFAULT NULL,
                                    "is_default" smallint NOT NULL DEFAULT 1,
                                    "desc" varchar(255) DEFAULT NULL
);

-- 8. storage_settings
DROP TABLE IF EXISTS "storage_settings";
CREATE TABLE "storage_settings" (
                                    "id" varchar(128) NOT NULL,
                                    "platform_identifier" varchar(128) NOT NULL,
                                    "config_data" json NOT NULL,
                                    "enabled" smallint NOT NULL DEFAULT 0,
                                    "workspace_id" varchar(128) NOT NULL,
                                    "created_at" timestamp DEFAULT NULL,
                                    "updated_at" timestamp DEFAULT NULL,
                                    "remark" varchar(255) DEFAULT NULL,
                                    "deleted" smallint DEFAULT 0,
                                    PRIMARY KEY ("id")
);

-- 9. sys_login_log (新增)
DROP TABLE IF EXISTS "sys_login_log";
CREATE TABLE "sys_login_log" (
                                 "id" BIGSERIAL PRIMARY KEY,
                                 "user_id" varchar(100) DEFAULT NULL,
                                 "username" varchar(50) NOT NULL DEFAULT '',
                                 "login_ip" varchar(50) NOT NULL,
                                 "login_address" varchar(255) DEFAULT NULL,
                                 "browser" varchar(255) DEFAULT NULL,
                                 "os" varchar(512) NOT NULL,
                                 "login_type" varchar(32) NOT NULL,
                                 "status" smallint NOT NULL,
                                 "msg" varchar(255) NOT NULL,
                                 "login_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. sys_permission
DROP TABLE IF EXISTS "sys_permission";
CREATE TABLE "sys_permission" (
                                  "id" SERIAL PRIMARY KEY,
                                  "permission_code" varchar(128) NOT NULL,
                                  "permission_name" varchar(128) NOT NULL,
                                  "module" varchar(64) NOT NULL,
                                  "description" varchar(255) DEFAULT NULL,
                                  "sort" int NOT NULL DEFAULT 0,
                                  "created_at" timestamp NOT NULL,
                                  "updated_at" timestamp NOT NULL,
                                  CONSTRAINT "uk_permission_code" UNIQUE ("permission_code")
);

-- 11. sys_role
DROP TABLE IF EXISTS "sys_role";
CREATE TABLE "sys_role" (
                            "id" SERIAL PRIMARY KEY,
                            "workspace_id" varchar(128) NOT NULL,
                            "role_code" varchar(255) NOT NULL,
                            "role_name" varchar(255) NOT NULL,
                            "description" text,
                            "role_type" smallint NOT NULL DEFAULT 1,
                            "created_at" timestamp NOT NULL,
                            "updated_at" timestamp NOT NULL,
                            CONSTRAINT "uk_workspace_role_code" UNIQUE ("workspace_id", "role_code")
);

-- 12. sys_role_permission
DROP TABLE IF EXISTS "sys_role_permission";
CREATE TABLE "sys_role_permission" (
                                       "id" SERIAL PRIMARY KEY,
                                       "role_id" int NOT NULL,
                                       "role_code" varchar(255) NOT NULL,
                                       "permission_code" varchar(128) NOT NULL,
                                       CONSTRAINT "uk_role_permission" UNIQUE ("role_id", "permission_code")
);

-- 13. sys_user
DROP TABLE IF EXISTS "sys_user";
CREATE TABLE "sys_user" (
                            "id" varchar(128) NOT NULL,
                            "username" varchar(128) NOT NULL,
                            "password" varchar(128) NOT NULL,
                            "email" varchar(128) NOT NULL,
                            "nickname" varchar(128) NOT NULL,
                            "avatar" varchar(255) DEFAULT NULL,
                            "status" int NOT NULL DEFAULT 0,
                            "created_at" timestamp NOT NULL,
                            "updated_at" timestamp NOT NULL,
                            "last_login_at" timestamp DEFAULT NULL,
                            PRIMARY KEY ("id")
);

-- 14. sys_user_transfer_setting
DROP TABLE IF EXISTS "sys_user_transfer_setting";
CREATE TABLE "sys_user_transfer_setting" (
                                             "id" SERIAL PRIMARY KEY,
                                             "user_id" varchar(128) NOT NULL,
                                             "download_location" varchar(255) DEFAULT NULL,
                                             "is_default_download_location" smallint NOT NULL DEFAULT 0,
                                             "download_speed_limit" int NOT NULL DEFAULT 5,
                                             "concurrent_upload_quantity" int NOT NULL DEFAULT 1,
                                             "concurrent_download_quantity" int NOT NULL DEFAULT 1,
                                             "chunk_size" bigint NOT NULL,
                                             "created_at" timestamp NOT NULL,
                                             "updated_at" timestamp NOT NULL,
                                             CONSTRAINT "uk_user_id" UNIQUE ("user_id")
);

-- 15. sys_workspace
DROP TABLE IF EXISTS "sys_workspace";
CREATE TABLE "sys_workspace" (
                                 "id" varchar(128) NOT NULL,
                                 "name" varchar(100) NOT NULL,
                                 "slug" varchar(64) NOT NULL,
                                 "description" varchar(500) DEFAULT NULL,
                                 "owner_id" varchar(128) NOT NULL,
                                 "member_count" int NOT NULL DEFAULT 1,
                                 "created_at" timestamp NOT NULL,
                                 "updated_at" timestamp NOT NULL,
                                 PRIMARY KEY ("id"),
                                 CONSTRAINT "uk_slug" UNIQUE ("slug")
);

-- 16. sys_workspace_invitation (新增)
DROP TABLE IF EXISTS "sys_workspace_invitation";
CREATE TABLE "sys_workspace_invitation" (
                                            "id" varchar(128) NOT NULL,
                                            "workspace_id" varchar(128) NOT NULL,
                                            "email" varchar(128) NOT NULL,
                                            "role_id" int NOT NULL,
                                            "invited_by" varchar(128) NOT NULL,
                                            "token" varchar(255) NOT NULL,
                                            "status" smallint NOT NULL DEFAULT 0,
                                            "expires_at" timestamp NOT NULL,
                                            "accepted_at" timestamp DEFAULT NULL,
                                            "created_at" timestamp NOT NULL,
                                            "updated_at" timestamp NOT NULL,
                                            PRIMARY KEY ("id"),
                                            CONSTRAINT "uk_token" UNIQUE ("token")
);

-- 17. sys_workspace_member
DROP TABLE IF EXISTS "sys_workspace_member";
CREATE TABLE "sys_workspace_member" (
                                        "id" BIGSERIAL PRIMARY KEY,
                                        "workspace_id" varchar(128) NOT NULL,
                                        "user_id" varchar(128) NOT NULL,
                                        "role_id" int NOT NULL,
                                        "joined_at" timestamp NOT NULL,
                                        "updated_at" timestamp NOT NULL,
                                        CONSTRAINT "uk_workspace_user" UNIQUE ("workspace_id", "user_id")
);


-- ==============================================================
-- 初始化数据插入
-- ==============================================================

-- 权限
INSERT INTO "sys_permission" ("id", "permission_code", "permission_name", "module", "description", "sort", "created_at", "updated_at") VALUES
                                                                                                                                           (1, 'file:read', '文件读取', '文件管理', '查看、预览、下载文件', 1, '2026-04-01 02:44:26', '2026-04-01 02:44:26'),
                                                                                                                                           (2, 'file:write', '文件编辑', '文件管理', '上传、创建文件夹、删除、移动、重命名、收藏、回收站操作', 2, '2026-04-01 02:44:26', '2026-04-01 02:44:26'),
                                                                                                                                           (3, 'file:share', '文件分享', '文件管理', '创建、管理、取消分享链接', 3, '2026-04-01 02:44:26', '2026-04-01 02:44:26'),
                                                                                                                                           (4, 'storage:manage', '存储管理', '存储管理', '存储源的增删改查及启用禁用', 4, '2026-04-01 02:44:26', '2026-04-01 02:44:26'),
                                                                                                                                           (5, 'member:manage', '成员管理', '系统管理', '邀请/移除成员、角色管理、权限查看', 5, '2026-04-01 02:44:26', '2026-04-01 02:44:26'),
                                                                                                                                           (6, 'log:read', '查看操作日志', '系统管理', '查看当前工作空间的文件、分享、成员、角色和存储操作记录', 6, '2026-07-22 00:00:00', '2026-07-22 00:00:00');

-- 用户
INSERT INTO "sys_user" ("id", "username", "password", "email", "nickname", "avatar", "status", "created_at", "updated_at", "last_login_at")
VALUES ('01jrvgs943q0f43h0aa5mjde0y', 'admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', '18202969762@163.com', 'xddcode', 'http://localhost:8080/files/free-fs/avatar/01jrvgs943q0f43h0aa5mjde0y/01jrvgs943q0f43h0aa5mjde0y_1775094747262.png', 0, '2025-04-15 09:25:22', '2026-04-21 11:29:18', '2026-04-21 11:29:18');

-- 工作空间
INSERT INTO "sys_workspace" ("id", "name", "slug", "description", "owner_id", "member_count", "created_at", "updated_at")
VALUES ('01kpq1bqzq1z99r0vd2xxqr3yk', 'xddcode的工作空间', 'xddcode-ws', NULL, '01jrvgs943q0f43h0aa5mjde0y', 1, '2026-04-21 11:29:22', '2026-04-21 11:29:22');

-- 角色
INSERT INTO "sys_role" ("id", "workspace_id", "role_code", "role_name", "description", "role_type", "created_at", "updated_at") VALUES
                                                                                                                                    (100015, '01kpq1bqzq1z99r0vd2xxqr3yk', 'admin', '空间管理员', '拥有全部权限', 0, '2026-04-21 11:29:22', '2026-04-21 11:29:22'),
                                                                                                                                    (100016, '01kpq1bqzq1z99r0vd2xxqr3yk', 'member', '普通成员', '可读写文件与分享，不可管理存储与成员', 0, '2026-04-21 11:29:22', '2026-04-21 11:29:22'),
                                                                                                                                    (100017, '01kpq1bqzq1z99r0vd2xxqr3yk', 'viewer', '受限成员', '仅可浏览、预览与下载', 0, '2026-04-21 11:29:23', '2026-04-21 11:29:23');

-- 角色权限关联
INSERT INTO "sys_role_permission" ("id", "role_id", "role_code", "permission_code") VALUES
                                                                                        (49, 100015, 'admin', 'file:read'), (50, 100015, 'admin', 'file:write'), (51, 100015, 'admin', 'file:share'),
                                                                                        (52, 100015, 'admin', 'storage:manage'), (53, 100015, 'admin', 'member:manage'), (54, 100016, 'member', 'file:read'),
                                                                                        (55, 100016, 'member', 'file:write'), (56, 100016, 'member', 'file:share'), (57, 100017, 'viewer', 'file:read'),
                                                                                        (58, 100015, 'admin', 'log:read');

-- 工作空间成员
INSERT INTO "sys_workspace_member" ("id", "workspace_id", "user_id", "role_id", "joined_at", "updated_at")
VALUES (8, '01kpq1bqzq1z99r0vd2xxqr3yk', '01jrvgs943q0f43h0aa5mjde0y', 100015, '2026-04-21 11:29:23', '2026-04-21 11:29:23');

-- 用户传输设置
INSERT INTO "sys_user_transfer_setting" ("id", "user_id", "download_location", "is_default_download_location", "download_speed_limit", "concurrent_upload_quantity", "concurrent_download_quantity", "chunk_size", "created_at", "updated_at")
VALUES (1, '01jrvgs943q0f43h0aa5mjde0y', 'C:\Users\insentek\Downloads', 1, -1, 3, 3, 5242880, '2025-11-11 14:45:27', '2026-04-03 11:19:24');

-- 重置序列
DROP TABLE IF EXISTS "sys_operation_log";
CREATE TABLE "sys_operation_log" (
  "id" bigserial PRIMARY KEY,
  "operator_id" varchar(128) DEFAULT NULL,
  "operator_name" varchar(128) DEFAULT NULL,
  "workspace_id" varchar(128) DEFAULT NULL,
  "operation_type" varchar(64) NOT NULL,
  "operation_name" varchar(128) NOT NULL,
  "target_type" varchar(32) DEFAULT NULL,
  "target_id" varchar(128) DEFAULT NULL,
  "target_name" varchar(255) DEFAULT NULL,
  "detail" text DEFAULT NULL,
  "operation_ip" varchar(50) DEFAULT NULL,
  "user_agent" varchar(512) DEFAULT NULL,
  "status" smallint NOT NULL DEFAULT 0,
  "error_message" varchar(512) DEFAULT NULL,
  "operation_time" timestamp NOT NULL
);
CREATE INDEX "idx_operation_workspace_time" ON "sys_operation_log" ("workspace_id", "operation_time");
CREATE INDEX "idx_operation_operator_time" ON "sys_operation_log" ("operator_id", "operation_time");
CREATE INDEX "idx_operation_type_time" ON "sys_operation_log" ("operation_type", "operation_time");

-- 重置序列
SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_role_id_seq', (SELECT MAX(id) FROM sys_role));
SELECT setval('sys_role_permission_id_seq', (SELECT MAX(id) FROM sys_role_permission));
SELECT setval('sys_workspace_member_id_seq', (SELECT MAX(id) FROM sys_workspace_member));
SELECT setval('sys_user_transfer_setting_id_seq', (SELECT MAX(id) FROM sys_user_transfer_setting));

SET session_replication_role = 'origin';
