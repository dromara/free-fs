package com.xddcodec.fs.interceptor;

import com.xddcodec.fs.framework.common.constant.RedisKey;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 文件预览拦截器，防盗链
 *
 * @Author: xddcode
 * @Date: 2026/03/13
 */
@Component
@Slf4j
public class PreviewInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisRepository repository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String uri = contextPath != null && !contextPath.isEmpty()
                ? requestUri.substring(contextPath.length())
                : requestUri;
        
        String token = request.getParameter("previewToken");
        if (token == null || token.isBlank()) {
            handleFailure(request, response, uri);
            return false;
        }
        
        String key = RedisKey.getPreviewTokenKey(token);
        Object cached = repository.get(key);
        
        if (cached == null) {
            handleFailure(request, response, uri);
            return false;
        }

        Object workspaceId = repository.get(RedisKey.getPreviewWorkspaceKey(token));
        if (workspaceId == null || String.valueOf(workspaceId).isBlank()) {
            handleFailure(request, response, uri);
            return false;
        }
        WorkspaceContext.setWorkspaceId(String.valueOf(workspaceId));
        
        // 验证 token 对应的资源
        String cacheValue = String.valueOf(cached);
        
        // 处理压缩包内文件预览：/archive/preview/{archiveFileId}?innerPath=xxx
        if (uri.startsWith("/archive/preview/")) {
            String archiveFileId = extractFileId(uri);
            String innerPath = request.getParameter("innerPath");
            String expectedValue = archiveFileId + "|" + innerPath;
            
            if (expectedValue.equals(cacheValue)) {
                return true;
            }
        }
        // 处理压缩包内文件流：/api/file/stream/preview/archive/inner/{tempId}
        else if (uri.contains("/archive/inner/")) {
            String tempId = extractFileId(uri);
            // 独立签发的流 token，Redis 值为对应的 tempId
            if (tempId != null && tempId.equals(cacheValue)) {
                return true;
            }
        }
        // 处理普通文件预览：/preview/{fileId}
        else {
            String fileId = extractFileId(uri);
            if (fileId != null && fileId.equals(cacheValue)) {
                return true;
            }
        }
        
        handleFailure(request, response, uri);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        WorkspaceContext.clear();
    }

    private void handleFailure(HttpServletRequest request, HttpServletResponse response, String uri) throws Exception {
        if (uri.startsWith("/preview/") || uri.startsWith("/archive/preview/")) {
            request.setAttribute("errorMessage", "预览链接已失效");
            request.setAttribute("errorDetail", "为了您的文件安全，请刷新列表重试。");
            request.getRequestDispatcher("/preview/error").forward(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Token");
        }
    }

    private String extractFileId(String uri) {
        if (uri == null || uri.isBlank()) return null;
        String normalized = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : null;
    }
}
