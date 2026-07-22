CREATE TABLE IF NOT EXISTS "sys_operation_log" (
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

CREATE INDEX IF NOT EXISTS "idx_operation_workspace_time"
  ON "sys_operation_log" ("workspace_id", "operation_time");
CREATE INDEX IF NOT EXISTS "idx_operation_operator_time"
  ON "sys_operation_log" ("operator_id", "operation_time");
CREATE INDEX IF NOT EXISTS "idx_operation_type_time"
  ON "sys_operation_log" ("operation_type", "operation_time");
