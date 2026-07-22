-- Repair the operation-log permission if it was imported through a latin1
-- MySQL client and keep the statement independent of the terminal encoding.
SET NAMES utf8mb4;

UPDATE `sys_permission`
SET `permission_name` = CONVERT(0xE69FA5E79C8BE6938DE4BD9CE697A5E5BF97 USING utf8mb4),
    `module` = CONVERT(0xE7B3BBE7BB9FE7AEA1E79086 USING utf8mb4),
    `description` = CONVERT(0xE69FA5E79C8BE5BD93E5898DE5B7A5E4BD9CE7A9BAE997B4E79A84E69687E4BBB6E38081E58886E4BAABE38081E68890E59198E38081E8A792E889B2E5928CE5AD98E582A8E6938DE4BD9CE8AEB0E5BD95 USING utf8mb4),
    `updated_at` = NOW()
WHERE `permission_code` = 'log:read';
