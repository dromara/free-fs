package com.xddcodec.fs.file.controller;

import com.xddcodec.fs.file.preview.ArchiveFilePreviewService;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.constant.RedisKey;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 压缩包内文件预览控制器
 */
@Slf4j
@Controller
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchivePreviewController {

    private final ArchiveFilePreviewService archiveFilePreviewService;
    private final FileInfoService fileInfoService;
    private final RedisRepository redisRepository;

    /**
     * 获取压缩包内文件预览 token
     * @param archiveFileId 压缩包文件ID
     * @param innerPath 压缩包内文件的路径
     */
    @ResponseBody
    @PostMapping("/preview/token/{archiveFileId}")
    public Result<String> previewToken(
            @PathVariable String archiveFileId,
            @RequestParam String innerPath) {
        
        fileInfoService.getAuthorizedFile(archiveFileId);

        // 生成 token
        String token = UUID.randomUUID().toString().replace("-", "");
        
        // 将 archiveFileId 和 innerPath 组合存储
        String cacheValue = archiveFileId + "|" + innerPath;
        redisRepository.setExpire(
                RedisKey.getPreviewTokenKey(token),
                cacheValue,
                RedisKey.PREVIEW_TOKEN_EXPIRE
        );
        redisRepository.setExpire(
                RedisKey.getPreviewWorkspaceKey(token),
                WorkspaceContext.getWorkspaceId(),
                RedisKey.PREVIEW_TOKEN_EXPIRE
        );

        log.info("生成压缩包内文件预览 token: archiveFileId={}, innerPath={}",
                archiveFileId, innerPath);
        
        return Result.ok(token);
    }

    /**
     * 预览压缩包内的文件
     * @param archiveFileId 压缩包文件ID
     * @param innerPath 压缩包内文件的路径
     * @param previewToken 预览 token
     */
    @GetMapping("/preview/{archiveFileId}")
    public String previewInnerFile(
            @PathVariable String archiveFileId,
            @RequestParam String innerPath,
            @RequestParam(required = false) String previewToken,
            Model model) {
        
        log.info("预览压缩包内文件: archiveFileId={}, innerPath={}",
                archiveFileId, innerPath);
        
        // 验证 token
        if (previewToken == null || previewToken.isBlank()) {
            model.addAttribute("errorMessage", I18nUtils.getMessage("preview.link.expired"));
            model.addAttribute("errorDetail", I18nUtils.getMessage("preview.token.missing"));
            return "preview/error";
        }
        
        String tokenKey = RedisKey.getPreviewTokenKey(previewToken);
        Object cached = redisRepository.get(tokenKey);
        
        if (cached == null) {
            model.addAttribute("errorMessage", I18nUtils.getMessage("preview.link.expired"));
            model.addAttribute("errorDetail", I18nUtils.getMessage("preview.token.security"));
            return "preview/error";
        }
        
        // 验证 token 对应的文件信息
        String cacheValue = String.valueOf(cached);
        String expectedValue = archiveFileId + "|" + innerPath;
        
        if (!expectedValue.equals(cacheValue)) {
            log.warn("Token 验证失败: expected={}, actual={}", expectedValue, cacheValue);
            model.addAttribute("errorMessage", I18nUtils.getMessage("preview.link.invalid"));
            model.addAttribute("errorDetail", I18nUtils.getMessage("preview.token.verify.failed"));
            return "preview/error";
        }
        
        // Token 验证通过，删除 token（一次性使用）
        // redisRepository.del(tokenKey); // 如果需要一次性消费，取消注释
        
        try {
            return archiveFilePreviewService.previewInnerFile(archiveFileId, innerPath, model);
        } catch (Exception e) {
            log.error("预览压缩包内文件失败: archiveFileId={}, innerPath={}", 
                    archiveFileId, innerPath, e);
            model.addAttribute("errorMessage", I18nUtils.getMessage("preview.failed"));
            model.addAttribute("errorDetail", e.getMessage());
            return "preview/error";
        }
    }
}
