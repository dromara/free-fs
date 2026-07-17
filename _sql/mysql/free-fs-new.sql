/*
 Navicat Premium Dump SQL

 Source Server         : mysql8
 Source Server Type    : MySQL
 Source Server Version : 80300 (8.3.0)
 Source Host           : localhost:3306
 Source Schema         : free-fs

 Target Server Type    : MySQL
 Target Server Version : 80300 (8.3.0)
 File Encoding         : 65001

 Date: 21/04/2026 11:30:40
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for file_info
-- ----------------------------
DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info`  (
  `id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `object_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源名称',
  `original_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源原始名称',
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源别名',
  `suffix` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '后缀名',
  `size` bigint NULL DEFAULT NULL COMMENT '大小',
  `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储标准MIME类型',
  `is_dir` tinyint(1) NOT NULL COMMENT '是否目录',
  `parent_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '父节点ID',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作空间ID',
  `user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id',
  `content_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用于秒传和文件校验',
  `storage_platform_setting_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储平台标识符',
  `upload_time` datetime NOT NULL COMMENT '上传时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `last_access_time` datetime NULL DEFAULT NULL COMMENT '最后访问时间',
  `is_deleted` tinyint(1) NULL DEFAULT NULL COMMENT '软删除标记，回收站标识0：未删除 1：已删除',
  `deleted_time` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_workspace_query`(`workspace_id` ASC, `user_id` ASC, `is_deleted` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id` ASC) USING BTREE,
  INDEX `idx_file_content_dedup`(`storage_platform_setting_id` ASC, `content_md5` ASC, `size` ASC, `is_dir` ASC) USING BTREE,
  INDEX `idx_file_object_reference`(`storage_platform_setting_id` ASC, `object_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件资源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of file_info
-- ----------------------------

-- ----------------------------
-- Table structure for file_share_access_record
-- ----------------------------
DROP TABLE IF EXISTS `file_share_access_record`;
CREATE TABLE `file_share_access_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `share_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分享ID',
  `access_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '访问IP',
  `access_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '访问地址',
  `browser` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浏览器类型',
  `os` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作系统',
  `access_time` datetime NOT NULL COMMENT '访问时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 77 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分享页面访问记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of file_share_access_record
-- ----------------------------

-- ----------------------------
-- Table structure for file_share_items
-- ----------------------------
DROP TABLE IF EXISTS `file_share_items`;
CREATE TABLE `file_share_items`  (
  `share_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分享ID',
  `file_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件/文件夹ID',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`share_id`, `file_id` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分享文件关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of file_share_items
-- ----------------------------

-- ----------------------------
-- Table structure for file_shares
-- ----------------------------
DROP TABLE IF EXISTS `file_shares`;
CREATE TABLE `file_shares`  (
  `id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分享ID',
  `user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分享人ID',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作空间ID',
  `share_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分享名称',
  `share_code` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '提取码（可为空）',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间（null表示永久有效）',
  `scope` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限范围: preview,download  (逗号分隔)',
  `view_count` int NULL DEFAULT 0 COMMENT '查看次数统计',
  `max_view_count` int NULL DEFAULT NULL COMMENT '最大查看次数（NULL表示无限制）',
  `download_count` int NULL DEFAULT 0 COMMENT '下载次数统计',
  `max_download_count` int NULL DEFAULT NULL COMMENT '最大下载次数（NULL表示无限制）',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件分享表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of file_shares
-- ----------------------------

-- ----------------------------
-- Table structure for file_transfer_task
-- ----------------------------
DROP TABLE IF EXISTS `file_transfer_task`;
CREATE TABLE `file_transfer_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务ID(UUID)',
  `upload_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '上传唯一ID',
  `parent_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父ID',
  `user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作空间ID',
  `storage_platform_setting_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储平台配置ID',
  `object_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对象key',
  `file_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '下载时关联的文件ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件MD5值',
  `suffix` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件类型(扩展名)',
  `mime_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储标准MIME类型',
  `total_chunks` int NOT NULL COMMENT '总分片数',
  `task_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务类型',
  `uploaded_chunks` int NULL DEFAULT 0 COMMENT '已上传分片数',
  `chunk_size` bigint NULL DEFAULT 5242880 COMMENT '分片大小(默认5MB)',
  `uploaded_size` bigint NULL DEFAULT 0 COMMENT '已上传大小(字节)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'uploading' COMMENT '状态',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_id`(`task_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_file_md5`(`file_md5` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`created_at` ASC) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 732 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '传输任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of file_transfer_task
-- ----------------------------

-- ----------------------------
-- Table structure for file_user_favorites
-- ----------------------------
DROP TABLE IF EXISTS `file_user_favorites`;
CREATE TABLE `file_user_favorites`  (
  `user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作空间ID',
  `file_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件ID',
  `favorite_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`workspace_id`, `user_id`, `file_id`) USING BTREE,
  INDEX `idx_file_time`(`file_id` ASC, `favorite_time` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of file_user_favorites
-- ----------------------------

-- ----------------------------
-- Table structure for storage_platform
-- ----------------------------
DROP TABLE IF EXISTS `storage_platform`;
CREATE TABLE `storage_platform`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '存储平台',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储平台名称',
  `identifier` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储平台标识符',
  `config_scheme` json NOT NULL COMMENT '存储平台配置描述schema',
  `icon` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储平台图标',
  `link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储平台链接',
  `is_default` tinyint NOT NULL DEFAULT 1 COMMENT '是否默认存储平台 0-否 1-是',
  `desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储平台描述',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 28 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '存储平台' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for storage_settings
-- ----------------------------
DROP TABLE IF EXISTS `storage_settings`;
CREATE TABLE `storage_settings`  (
  `id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'id',
  `platform_identifier` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储平台标识符',
  `config_data` json NOT NULL COMMENT '存储配置',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用 0：否 1：是',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作空间ID',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '存储平台配置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of storage_settings
-- ----------------------------

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户编号',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '用户账号',
  `login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录IP',
  `login_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录地址',
  `browser` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浏览器类型',
  `os` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作系统',
  `login_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录方式',
  `status` tinyint NOT NULL COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '提示消息',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4543 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统访问记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码，如 file:upload',
  `permission_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限名称，如 上传文件',
  `module` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属模块，如 文件管理',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '权限描述',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_permission_code`(`permission_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_permission
-- ----------------------------
INSERT INTO `sys_permission` VALUES (1, 'file:read', '文件读取', '文件管理', '查看、预览、下载文件', 1, '2026-04-01 02:44:26', '2026-04-01 02:44:26');
INSERT INTO `sys_permission` VALUES (2, 'file:write', '文件编辑', '文件管理', '上传、创建文件夹、删除、移动、重命名、收藏、回收站操作', 2, '2026-04-01 02:44:26', '2026-04-01 02:44:26');
INSERT INTO `sys_permission` VALUES (3, 'file:share', '文件分享', '文件管理', '创建、管理、取消分享链接', 3, '2026-04-01 02:44:26', '2026-04-01 02:44:26');
INSERT INTO `sys_permission` VALUES (4, 'storage:manage', '存储管理', '存储管理', '存储源的增删改查及启用禁用', 4, '2026-04-01 02:44:26', '2026-04-01 02:44:26');
INSERT INTO `sys_permission` VALUES (5, 'member:manage', '成员管理', '系统管理', '邀请/移除成员、角色管理、权限查看', 5, '2026-04-01 02:44:26', '2026-04-01 02:44:26');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作空间ID',
  `role_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  `role_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '角色描述',
  `role_type` tinyint NOT NULL DEFAULT 1 COMMENT '0=系统预设 1=自定义',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_workspace_role_code`(`workspace_id` ASC, `role_code` ASC) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100018 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (100015, '01kpq1bqzq1z99r0vd2xxqr3yk', 'admin', '空间管理员', '拥有全部权限', 0, '2026-04-21 11:29:22', '2026-04-21 11:29:22');
INSERT INTO `sys_role` VALUES (100016, '01kpq1bqzq1z99r0vd2xxqr3yk', 'member', '普通成员', '可读写文件与分享，不可管理存储与成员', 0, '2026-04-21 11:29:22', '2026-04-21 11:29:22');
INSERT INTO `sys_role` VALUES (100017, '01kpq1bqzq1z99r0vd2xxqr3yk', 'viewer', '受限成员', '仅可浏览、预览与下载', 0, '2026-04-21 11:29:23', '2026-04-21 11:29:23');

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `role_id` int NOT NULL COMMENT '角色ID',
  `role_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_permission`(`role_id` ASC, `permission_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 58 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_permission
-- ----------------------------
INSERT INTO `sys_role_permission` VALUES (49, 100015, 'admin', 'file:read');
INSERT INTO `sys_role_permission` VALUES (50, 100015, 'admin', 'file:write');
INSERT INTO `sys_role_permission` VALUES (51, 100015, 'admin', 'file:share');
INSERT INTO `sys_role_permission` VALUES (52, 100015, 'admin', 'storage:manage');
INSERT INTO `sys_role_permission` VALUES (53, 100015, 'admin', 'member:manage');
INSERT INTO `sys_role_permission` VALUES (54, 100016, 'member', 'file:read');
INSERT INTO `sys_role_permission` VALUES (55, 100016, 'member', 'file:write');
INSERT INTO `sys_role_permission` VALUES (56, 100016, 'member', 'file:share');
INSERT INTO `sys_role_permission` VALUES (57, 100017, 'viewer', 'file:read');

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `username` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邮箱',
  `nickname` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  `status` int NOT NULL DEFAULT 0 COMMENT '用户状态 0正常 1禁用',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  `last_login_at` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES ('01jrvgs943q0f43h0aa5mjde0y', 'admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', '18202969762@163.com', 'xddcode', 'http://localhost:8080/files/free-fs/avatar/01jrvgs943q0f43h0aa5mjde0y/01jrvgs943q0f43h0aa5mjde0y_1775094747262.png', 0, '2025-04-15 09:25:22', '2026-04-21 11:29:18', '2026-04-21 11:29:18');

-- ----------------------------
-- Table structure for sys_user_transfer_setting
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_transfer_setting`;
CREATE TABLE `sys_user_transfer_setting`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `download_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件下载位置',
  `is_default_download_location` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认该路径为下载路径，如果否则每次下载询问保存地址',
  `download_speed_limit` int NOT NULL DEFAULT 5 COMMENT '下载速率限制 单位：MB/S',
  `concurrent_upload_quantity` int NOT NULL DEFAULT 1 COMMENT '并发上传数量',
  `concurrent_download_quantity` int NOT NULL DEFAULT 1 COMMENT '并发下载数量',
  `chunk_size` bigint NOT NULL COMMENT '分片大小',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id`(`user_id` ASC) USING BTREE COMMENT '用户ID唯一索引'
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户传输设置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_transfer_setting
-- ----------------------------
INSERT INTO `sys_user_transfer_setting` VALUES (1, '01jrvgs943q0f43h0aa5mjde0y', 'C:\\Users\\insentek\\Downloads', 1, -1, 3, 3, 5242880, '2025-11-11 14:45:27', '2026-04-03 11:19:24');

-- ----------------------------
-- Table structure for sys_workspace
-- ----------------------------
DROP TABLE IF EXISTS `sys_workspace`;
CREATE TABLE `sys_workspace`  (
  `id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作空间ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作空间名称',
  `slug` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'URL友好的唯一标识',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作空间描述',
  `owner_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建者/拥有者用户ID',
  `member_count` int NOT NULL DEFAULT 1 COMMENT '成员数量（冗余字段，便于列表展示）',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作空间表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_workspace
-- ----------------------------
INSERT INTO `sys_workspace` VALUES ('01kpq1bqzq1z99r0vd2xxqr3yk', 'xddcode的工作空间', 'xddcode-ws', NULL, '01jrvgs943q0f43h0aa5mjde0y', 1, '2026-04-21 11:29:22', '2026-04-21 11:29:22');

-- ----------------------------
-- Table structure for sys_workspace_invitation
-- ----------------------------
DROP TABLE IF EXISTS `sys_workspace_invitation`;
CREATE TABLE `sys_workspace_invitation`  (
  `id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邀请ID',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作空间ID',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '被邀请人邮箱',
  `role_id` int NOT NULL COMMENT '分配的角色ID',
  `invited_by` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邀请人用户ID',
  `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邀请令牌（用于注册链接）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待接受 1-已接受 2-已过期 3-已取消',
  `expires_at` datetime NOT NULL COMMENT '邀请过期时间',
  `accepted_at` datetime NULL DEFAULT NULL COMMENT '接受时间',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_token`(`token` ASC) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id` ASC) USING BTREE,
  INDEX `idx_email_status`(`email` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作空间邀请表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_workspace_invitation
-- ----------------------------

-- ----------------------------
-- Table structure for sys_workspace_member
-- ----------------------------
DROP TABLE IF EXISTS `sys_workspace_member`;
CREATE TABLE `sys_workspace_member`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作空间ID',
  `user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `role_id` int NOT NULL COMMENT '该成员在此工作空间的角色ID',
  `joined_at` datetime NOT NULL COMMENT '加入时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_workspace_user`(`workspace_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作空间成员表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_workspace_member
-- ----------------------------
INSERT INTO `sys_workspace_member` VALUES (8, '01kpq1bqzq1z99r0vd2xxqr3yk', '01jrvgs943q0f43h0aa5mjde0y', 100015, '2026-04-21 11:29:23', '2026-04-21 11:29:23');

SET FOREIGN_KEY_CHECKS = 1;
