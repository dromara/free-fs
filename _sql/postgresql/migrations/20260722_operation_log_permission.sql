-- Add a standalone permission for viewing workspace operation logs.
INSERT INTO "sys_permission"
    ("permission_code", "permission_name", "module", "description", "sort", "created_at", "updated_at")
VALUES
    ('log:read', '查看操作日志', '系统管理',
     '查看当前工作空间的文件、分享、成员、角色和存储操作记录', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT ("permission_code") DO NOTHING;

-- Existing workspace administrators keep access after the endpoint switches
-- from member:manage to log:read. Custom roles can opt in independently.
INSERT INTO "sys_role_permission" ("role_id", "role_code", "permission_code")
SELECT r."id", r."role_code", 'log:read'
FROM "sys_role" r
WHERE r."role_code" = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM "sys_role_permission" rp
      WHERE rp."role_id" = r."id"
        AND rp."permission_code" = 'log:read'
  );
