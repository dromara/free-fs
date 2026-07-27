package com.xddcodec.fs.log.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xddcodec.fs.framework.common.utils.DateUtils;
import com.xddcodec.fs.log.domain.SysOperationLog;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志展示对象。
 */
@Data
@AutoMapper(target = SysOperationLog.class)
public class SysOperationLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    private Integer status;

    private String errorMessage;

    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private LocalDateTime operationTime;
}
