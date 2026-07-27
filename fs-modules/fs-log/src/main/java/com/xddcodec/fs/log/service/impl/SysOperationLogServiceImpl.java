package com.xddcodec.fs.log.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.framework.common.utils.IpUtils;
import com.xddcodec.fs.log.domain.SysOperationLog;
import com.xddcodec.fs.log.domain.dto.OperationLogPageQry;
import com.xddcodec.fs.log.domain.vo.SysOperationLogVO;
import com.xddcodec.fs.log.mapper.SysOperationLogMapper;
import com.xddcodec.fs.log.service.SysOperationLogService;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.xddcodec.fs.log.domain.table.SysOperationLogTableDef.SYS_OPERATION_LOG;

/**
 * 操作日志服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperationLogServiceImpl
        extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
        implements SysOperationLogService {

    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_FAILURE = 1;

    private final Converter converter;

    @Override
    public PageResult<SysOperationLogVO> getPages(OperationLogPageQry qry) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        int pageNumber = qry.getPage() == null ? 1 : qry.getPage();
        int pageSize = qry.getPageSize() == null ? 20 : qry.getPageSize();
        Page<SysOperationLog> page = new Page<>(pageNumber, pageSize);

        QueryWrapper wrapper = new QueryWrapper()
                .where(SYS_OPERATION_LOG.WORKSPACE_ID.eq(workspaceId));

        if (StrUtil.isNotBlank(qry.getKeyword())) {
            String keyword = "%" + qry.getKeyword().trim() + "%";
            wrapper.and(
                    SYS_OPERATION_LOG.OPERATOR_NAME.like(keyword)
                            .or(SYS_OPERATION_LOG.OPERATOR_ID.like(keyword))
                            .or(SYS_OPERATION_LOG.OPERATION_NAME.like(keyword))
                            .or(SYS_OPERATION_LOG.TARGET_NAME.like(keyword))
                            .or(SYS_OPERATION_LOG.DETAIL.like(keyword))
                            .or(SYS_OPERATION_LOG.OPERATION_IP.like(keyword))
            );
        }
        if (StrUtil.isNotBlank(qry.getOperationType())) {
            wrapper.and(SYS_OPERATION_LOG.OPERATION_TYPE.eq(qry.getOperationType().trim()));
        }
        if (qry.getStatus() != null) {
            wrapper.and(SYS_OPERATION_LOG.STATUS.eq(qry.getStatus()));
        }
        wrapper.orderBy(SYS_OPERATION_LOG.OPERATION_TIME.desc())
                .orderBy(SYS_OPERATION_LOG.ID.desc());

        this.page(page, wrapper);
        List<SysOperationLogVO> records =
                converter.convert(page.getRecords(), SysOperationLogVO.class);
        return PageResult.success(records, page.getTotalRow());
    }

    @Override
    public void recordSuccess(String operationType,
                              String operationName,
                              String targetType,
                              String targetId,
                              String targetName,
                              String detail) {
        record(WorkspaceContext.getWorkspaceId(), operationType, operationName,
                targetType, targetId, targetName, detail, STATUS_SUCCESS, null, null, null);
    }

    @Override
    public void recordSuccess(String workspaceId,
                              String operationType,
                              String operationName,
                              String targetType,
                              String targetId,
                              String targetName,
                              String detail) {
        record(workspaceId, operationType, operationName,
                targetType, targetId, targetName, detail, STATUS_SUCCESS, null, null, null);
    }

    @Override
    public void recordSuccessAs(String workspaceId,
                                String operatorId,
                                String operatorName,
                                String operationType,
                                String operationName,
                                String targetType,
                                String targetId,
                                String targetName,
                                String detail) {
        record(workspaceId, operationType, operationName,
                targetType, targetId, targetName, detail, STATUS_SUCCESS, null,
                operatorId, operatorName);
    }

    @Override
    public void recordFailure(String operationType,
                              String operationName,
                              String targetType,
                              String targetId,
                              String targetName,
                              String detail,
                              String errorMessage) {
        record(WorkspaceContext.getWorkspaceId(), operationType, operationName,
                targetType, targetId, targetName, detail, STATUS_FAILURE, errorMessage, null, null);
    }

    private void record(String workspaceId,
                        String operationType,
                        String operationName,
                        String targetType,
                        String targetId,
                        String targetName,
                        String detail,
                        int status,
                        String errorMessage,
                        String explicitOperatorId,
                        String explicitOperatorName) {
        try {
            SysOperationLog operationLog = new SysOperationLog();
            if (StrUtil.isNotBlank(explicitOperatorId)) {
                operationLog.setOperatorId(explicitOperatorId);
                operationLog.setOperatorName(StrUtil.blankToDefault(explicitOperatorName, explicitOperatorId));
            } else if (StpUtil.isLogin()) {
                String operatorId = StpUtil.getLoginIdAsString();
                operationLog.setOperatorId(operatorId);
                Object username = StpUtil.getSession().get("username");
                operationLog.setOperatorName(username == null ? operatorId : String.valueOf(username));
            }
            operationLog.setWorkspaceId(workspaceId);
            operationLog.setOperationType(trim(operationType, 64));
            operationLog.setOperationName(trim(operationName, 128));
            operationLog.setTargetType(trim(targetType, 32));
            operationLog.setTargetId(trim(targetId, 128));
            operationLog.setTargetName(trim(targetName, 255));
            operationLog.setDetail(trim(detail, 4000));
            operationLog.setOperationIp(trim(IpUtils.getIpAddr(), 50));
            operationLog.setUserAgent(trim(IpUtils.getUserAgent(), 512));
            operationLog.setStatus(status);
            operationLog.setErrorMessage(trim(errorMessage, 512));
            operationLog.setOperationTime(LocalDateTime.now());
            this.save(operationLog);
        } catch (Exception e) {
            // 日志记录失败不能影响正常业务操作。
            log.error("记录操作日志失败: type={}, targetId={}", operationType, targetId, e);
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
