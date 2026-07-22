package com.xddcodec.fs.log.domain.dto;

import com.xddcodec.fs.framework.common.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogPageQry extends PageQuery {

    private String keyword;

    private String operationType;

    private Integer status;
}
