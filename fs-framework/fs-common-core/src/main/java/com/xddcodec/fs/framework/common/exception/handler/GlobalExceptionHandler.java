package com.xddcodec.fs.framework.common.exception.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.exception.ErrorCode;
import com.xddcodec.fs.framework.common.exception.StorageConfigException;
import com.xddcodec.fs.framework.common.exception.StorageOperationException;
import com.xddcodec.fs.framework.common.utils.ErrorMessageUtils;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;

/**
 * 全局异常处理
 *
 * @Author: xddcode
 * @Date: 2023/6/29 17:27
 */
@Slf4j
@RestControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    /**
     * 权限码异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public Result<?> handleNotPermissionException(NotPermissionException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限码校验失败'{}'", requestURI, e.getMessage());
        return Result.error(ErrorCode.FORBIDDEN.getCode(), I18nUtils.getMessage("auth.forbidden"), null);
    }

    /**
     * 角色权限异常
     */
    @ExceptionHandler(NotRoleException.class)
    public Result<?> handleNotRoleException(NotRoleException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',角色权限校验失败'{}'", requestURI, e.getMessage());
        return Result.error(ErrorCode.FORBIDDEN.getCode(), I18nUtils.getMessage("auth.forbidden"), null);
    }

    /**
     * 认证失败
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handlerNotLoginException(NotLoginException nle) {
        ErrorCode errorCode;
        String i18nKey;
        
        switch (nle.getType()) {
            case NotLoginException.NOT_TOKEN -> {
                errorCode = ErrorCode.NOT_TOKEN;
                i18nKey = "auth.not.token";
            }
            case NotLoginException.INVALID_TOKEN -> {
                errorCode = ErrorCode.INVALID_TOKEN;
                i18nKey = "auth.invalid.token";
            }
            case NotLoginException.TOKEN_TIMEOUT -> {
                errorCode = ErrorCode.EXPIRED_TOKEN;
                i18nKey = "auth.expired.token";
            }
            case NotLoginException.BE_REPLACED -> {
                errorCode = ErrorCode.REPLACED_TOKEN;
                i18nKey = "auth.replaced.token";
            }
            case NotLoginException.KICK_OUT -> {
                errorCode = ErrorCode.KICK_OUT_TOKEN;
                i18nKey = "auth.kick.out.token";
            }
            case NotLoginException.NO_PREFIX -> {
                errorCode = ErrorCode.NO_PREFIX_MESSAGE_TOKEN;
                i18nKey = "auth.no.prefix.token";
            }
            default -> {
                errorCode = ErrorCode.UNAUTHORIZED;
                i18nKey = "auth.unauthorized";
            }
        }
        
        log.error("Token exception [{}]: {}", errorCode.getCode(), nle.getMessage(), nle);
        return Result.error(errorCode.getCode(), I18nUtils.getMessage(i18nKey), null);
    }

    /**
     * 主键或UNIQUE索引，数据重复异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',数据库中已存在记录'{}'", requestURI, e.getMessage());
        return Result.error(I18nUtils.getMessage("db.duplicate.key"));
    }

    /**
     * IllegalArgumentException 异常处理返回json
     */
    @ExceptionHandler({IllegalArgumentException.class})
    public Result<?> badRequestException(IllegalArgumentException e) {
        return defHandler(e.getMessage(), e);
    }

    /**
     * BusinessException 业务异常处理，保留业务层给出的状态码。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage(), null);
    }


    /**
     * StorageOperationException 存储异常处理
     *
     * @param e
     * @return
     */
    @ExceptionHandler(StorageOperationException.class)
    public Result<?> handleStorageOperationException(StorageOperationException e) {
        String userFriendlyMessage = ErrorMessageUtils.extractUserFriendlyMessage(e);
        log.error("存储操作异常: {}", e.getMessage(), e);
        return Result.error(userFriendlyMessage);
    }

    /**
     * StorageConfigException 存储配置异常处理
     * 返回状态码:500
     */
    @ExceptionHandler(StorageConfigException.class)
    public Result<?> handleStorageConfigException(StorageConfigException e) {
        String userFriendlyMessage = ErrorMessageUtils.extractUserFriendlyMessage(e);
        log.error("存储配置异常: {}", e.getMessage(), e);
        return Result.error(userFriendlyMessage);
    }

    /**
     * HttpRequestMethodNotSupportedException 异常处理返回json
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    public Result<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return defHandler(I18nUtils.getMessage("request.method.not.supported"), e);
    }


    /**
     * 文件上传相关异常（包括大小超限）
     */
    @ExceptionHandler({
            MaxUploadSizeExceededException.class,
            org.springframework.web.multipart.MultipartException.class
    })
    public Result<?> handleFileUploadException(Exception e) {
        log.error("文件上传异常: {}", e.getClass().getName(), e);
        String message = I18nUtils.getMessage("file.upload.failed") + ": " + e.getMessage();
        return Result.error(HttpStatus.BAD_REQUEST.value(), message, null);
    }

    /**
     * Bean 校验异常 Validate
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.error(message, e);
        return Result.error(HttpStatus.BAD_REQUEST.value(), message, null);
    }

    /**
     * 方法参数校验异常 Validate
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleValidationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().iterator().next().getMessage();
        log.error(message, e);
        return Result.error(HttpStatus.BAD_REQUEST.value(), message, null);
    }

    /**
     * 方法参数校验异常 Validate
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = bindingResult.getAllErrors().iterator().next().getDefaultMessage();
        log.error(message, e);
        return Result.error(HttpStatus.BAD_REQUEST.value(), message, null);
    }

    /**
     * 处理静态资源未找到异常
     * 通常是浏览器请求 sw.js、manifest.json 等前端资源
     * 这些请求来自浏览器缓存的 Service Worker 注册，可以安全忽略
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        // 针对根路径 "/" 的特殊友好提示
        if ("/".equals(requestURI)) {
            return Result.error(HttpStatus.NOT_FOUND.value(),
                    I18nUtils.getMessage("request.root.path.message"), null);
        }

        // 只对特定的前端资源请求使用 debug 级别日志，避免日志污染
        if (requestURI.matches(".*(sw\\.js|manifest\\.json|robots\\.txt|favicon\\.ico)$")) {
            log.debug("忽略前端资源请求: {}", requestURI);
            return null;
        }
        // 其他资源未找到仍然记录为警告
        log.warn("请求地址'{}',静态资源未找到", requestURI);
        return Result.error(HttpStatus.NOT_FOUND.value(), I18nUtils.getMessage("request.resource.not.found"), null);
    }

    /**
     * 处理客户端断开连接异常
     * 这种异常通常发生在用户刷新页面、快速切换文件、网络中断等场景
     * 不需要记录为错误日志
     */
    @ExceptionHandler({
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class,
            java.io.IOException.class
    })
    public Result<?> handleClientAbortException(Exception e) {
        if (isClientAbortException(e)) {
            log.debug("客户端断开连接: {}", e.getMessage());
            // 返回空结果，避免二次异常
            return null;
        }
        // 不是客户端断开异常，按正常异常处理
        return defHandler(I18nUtils.getMessage("system.error"), e);
    }

    /**
     * 处理所有不可知的异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        return defHandler(I18nUtils.getMessage("system.error"), e);
    }

    /**
     * 统一返回
     */
    private Result<?> defHandler(String msg, Exception e) {
        log.error(msg, e);
        return Result.error(msg);
    }

    /**
     * 判断是否为客户端断开连接异常
     */
    private boolean isClientAbortException(Throwable e) {
        if (e == null) {
            return false;
        }

        String className = e.getClass().getName();
        String message = e.getMessage();

        // 检查异常类型
        if (className.contains("ClientAbortException")
                || className.contains("AsyncRequestNotUsableException")
                || className.contains("EOFException")
                || className.contains("SocketException")) {
            return true;
        }

        // 检查异常消息
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("broken pipe")
                    || lowerMessage.contains("connection reset")
                    || lowerMessage.contains("connection abort")
                    || lowerMessage.contains("stream closed")
                    || lowerMessage.contains("client abort")
                    || lowerMessage.contains("outputstream failed")) {
                return true;
            }
        }

        // 递归检查 cause
        return isClientAbortException(e.getCause());
    }
}
