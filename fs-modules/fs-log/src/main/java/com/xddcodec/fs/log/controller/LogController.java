package com.xddcodec.fs.log.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.log.domain.dto.LoginLogPageQry;
import com.xddcodec.fs.log.domain.dto.OperationLogPageQry;
import com.xddcodec.fs.log.domain.vo.SysLoginLogVO;
import com.xddcodec.fs.log.domain.vo.SysOperationLogVO;
import com.xddcodec.fs.log.service.SysLoginLogService;
import com.xddcodec.fs.log.service.SysOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志控制器
 *
 * @Author: xddcodec
 * @Date: 2025/9/25 16:16
 */
@Validated
@RestController
@RequestMapping("/apis/logs")
@RequiredArgsConstructor
@Tag(name = "日志管理")
public class LogController {

    private final SysLoginLogService loginLogService;
    private final SysOperationLogService operationLogService;

    @Operation(summary = "分页获取登录日志列表")
    @GetMapping("/login/pages")
    public PageResult<SysLoginLogVO> getPages(LoginLogPageQry qry) {
        return loginLogService.getPages(qry);
    }

    @Operation(summary = "分页获取当前工作空间操作日志")
    @GetMapping("/operation/pages")
    @SaCheckPermission("log:read")
    public PageResult<SysOperationLogVO> getOperationPages(OperationLogPageQry qry) {
        return operationLogService.getPages(qry);
    }
}
