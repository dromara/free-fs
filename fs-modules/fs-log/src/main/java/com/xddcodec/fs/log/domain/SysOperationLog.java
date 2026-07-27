package com.xddcodec.fs.log.domain;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作空间操作日志。
 */
@Data
@Table("sys_operation_log")
public class SysOperationLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String operatorId;

    private String operatorName;

    private String workspaceId;

    private String operationType;

    private String operationName;

    private String targetType;

    private String targetId;

    private String targetName;

    private String detail;

    private String operationIp;

    private String userAgent;

    /**
     * 0 成功，1 失败。
     */
    private Integer status;

    private String errorMessage;

    private LocalDateTime operationTime;
}
