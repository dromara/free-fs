package com.xddcodec.fs.file.controller;

import com.xddcodec.fs.file.preview.PreviewService;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.constant.RedisKey;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FilePreviewController {

    private final PreviewService previewService;

    private final FileInfoService fileInfoService;

    private final RedisRepository redisRepository;

    /**
     * 获取预览token
     */
    @ResponseBody
    @PostMapping("/preview/token/{fileId}")
    public Result<String> previewToken(@PathVariable String fileId) {
        fileInfoService.getAuthorizedFile(fileId);
        String token = UUID.randomUUID().toString().replace("-", "");
        redisRepository.setExpire(
                RedisKey.getPreviewTokenKey(token),
                fileId,
                RedisKey.PREVIEW_TOKEN_EXPIRE
        );
        redisRepository.setExpire(
                RedisKey.getPreviewWorkspaceKey(token),
                WorkspaceContext.getWorkspaceId(),
                RedisKey.PREVIEW_TOKEN_EXPIRE
        );
        return Result.ok(token);
    }

    /**
     * 文件预览入口
     */
    @GetMapping("/preview/{fileId}")
    public String preview(@PathVariable String fileId, Model model) {
        log.info("收到预览请求: fileId={}", fileId);

        try {
            return previewService.preview(fileId, model);
        } catch (Exception e) {
            log.error("预览过程发生未捕获异常: fileId={}", fileId, e);
            model.addAttribute("errorMessage", I18nUtils.getMessage("preview.system.error"));
            model.addAttribute("errorDetail", I18nUtils.getMessage("preview.unexpected.error"));
            return "preview/error";
        }
    }

    @GetMapping("/preview/error")
    public String previewError(HttpServletRequest request, Model model) {
        Object errorMessage = request.getAttribute("errorMessage");
        Object errorDetail = request.getAttribute("errorDetail");
        model.addAttribute("errorMessage",
                errorMessage != null ? errorMessage : I18nUtils.getMessage("preview.failed"));
        model.addAttribute("errorDetail",
                errorDetail != null ? errorDetail : I18nUtils.getMessage("preview.retry"));
        return "preview/error";
    }
}
