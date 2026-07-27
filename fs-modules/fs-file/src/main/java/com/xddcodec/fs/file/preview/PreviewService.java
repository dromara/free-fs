package com.xddcodec.fs.file.preview;

import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.enums.FileTypeEnum;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.preview.config.FilePreviewConfig;
import com.xddcodec.fs.framework.preview.core.PreviewContext;
import com.xddcodec.fs.framework.preview.core.PreviewStrategy;
import com.xddcodec.fs.framework.preview.factory.PreviewStrategyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.LocalDateTime;

/**
 * 预览服务
 */
@Service
@RequiredArgsConstructor
public class PreviewService {
    private static final String BROWSER_PREVIEW_STREAM_PATH = "/api/file/stream/preview";

    private final FileInfoService fileInfoService;
    private final PreviewStrategyManager strategyManager;
    private final FilePreviewConfig previewConfig;

    public String preview(String fileId, Model model) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return buildErrorPage(model, I18nUtils.getMessage("file.id.invalid"), I18nUtils.getMessage("file.id.empty"));
        }
        FileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) {
            return buildErrorPage(model, I18nUtils.getMessage("file.not.found"), I18nUtils.getMessage("file.not.exist.or.deleted"));
        }

        Long maxFileSize = previewConfig.getMaxFileSize();
        if (maxFileSize != null && maxFileSize > 0 && fileInfo.getSize() > maxFileSize) {
            return buildErrorPage(model, I18nUtils.getMessage("file.too.large"),
                    I18nUtils.getMessage("file.size.limit.exceeded", 
                            new Object[]{maxFileSize / 1024 / 1024}));
        }

        FileTypeEnum fileType = FileTypeEnum.fromFileName(fileInfo.getDisplayName());
        PreviewStrategy strategy = strategyManager.getStrategy(fileType);
        if (strategy == null) {
            return buildErrorPage(model, I18nUtils.getMessage("preview.file.type.not.supported"), 
                    I18nUtils.getMessage("preview.file.type.not.supported.detail"));
        }

        PreviewContext context = PreviewContext.builder()
                .fileId(fileId)
                .fileName(fileInfo.getDisplayName())
                .streamUrl(BROWSER_PREVIEW_STREAM_PATH + "/" + fileId)
                .fileSize(fileInfo.getSize())
                .extension(fileInfo.getSuffix())
                .fileType(fileType)
                .build();
        strategy.fillModel(context, model);

        //修改文件访问记录
        fileInfo.setLastAccessTime(LocalDateTime.now());
        fileInfoService.updateById(fileInfo);
        return strategy.getTemplatePath();
    }

    /**
     * 构建错误页面
     */
    private String buildErrorPage(Model model, String errorMessage, String errorDetail) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("errorDetail", errorDetail);
        return "preview/error";
    }
}
