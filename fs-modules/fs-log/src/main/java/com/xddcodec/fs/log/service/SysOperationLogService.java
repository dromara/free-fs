package com.xddcodec.fs.log.service;

import com.mybatisflex.core.service.IService;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.log.domain.SysOperationLog;
import com.xddcodec.fs.log.domain.dto.OperationLogPageQry;
import com.xddcodec.fs.log.domain.vo.SysOperationLogVO;

/**
 * 操作日志服务。
 */
public interface SysOperationLogService extends IService<SysOperationLog> {

    PageResult<SysOperationLogVO> getPages(OperationLogPageQry qry);

    void recordSuccess(String operationType,
                       String operationName,
                       String targetType,
                       String targetId,
                       String targetName,
                       String detail);

    void recordSuccess(String workspaceId,
                       String operationType,
                       String operationName,
                       String targetType,
                       String targetId,
                       String targetName,
                       String detail);

    void recordSuccessAs(String workspaceId,
                         String operatorId,
                         String operatorName,
                         String operationType,
                         String operationName,
                         String targetType,
                         String targetId,
                         String targetName,
                         String detail);

    void recordFailure(String operationType,
                       String operationName,
                       String targetType,
                       String targetId,
                       String targetName,
                       String detail,
                       String errorMessage);
}
