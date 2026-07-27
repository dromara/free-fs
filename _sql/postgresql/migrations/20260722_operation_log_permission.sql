-- Add a standalone permission for viewing workspace operation logs.
SET client_encoding TO 'UTF8';

INSERT INTO "sys_permission"
    ("permission_code", "permission_name", "module", "description", "sort", "created_at", "updated_at")
VALUES
    ('log:read',
     convert_from(decode('E69FA5E79C8BE6938DE4BD9CE697A5E5BF97', 'hex'), 'UTF8'),
     convert_from(decode('E7B3BBE7BB9FE7AEA1E79086', 'hex'), 'UTF8'),
     convert_from(decode('E69FA5E79C8BE5BD93E5898DE5B7A5E4BD9CE7A9BAE997B4E79A84E69687E4BBB6E38081E58886E4BAABE38081E68890E59198E38081E8A792E889B2E5928CE5AD98E582A8E6938DE4BD9CE8AEB0E5BD95', 'hex'), 'UTF8'),
     6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT ("permission_code") DO NOTHING;

UPDATE "sys_permission"
SET "permission_name" = convert_from(decode('E69FA5E79C8BE6938DE4BD9CE697A5E5BF97', 'hex'), 'UTF8'),
    "module" = convert_from(decode('E7B3BBE7BB9FE7AEA1E79086', 'hex'), 'UTF8'),
    "description" = convert_from(decode('E69FA5E79C8BE5BD93E5898DE5B7A5E4BD9CE7A9BAE997B4E79A84E69687E4BBB6E38081E58886E4BAABE38081E68890E59198E38081E8A792E889B2E5928CE5AD98E582A8E6938DE4BD9CE8AEB0E5BD95', 'hex'), 'UTF8'),
    "updated_at" = CURRENT_TIMESTAMP
WHERE "permission_code" = 'log:read';

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
