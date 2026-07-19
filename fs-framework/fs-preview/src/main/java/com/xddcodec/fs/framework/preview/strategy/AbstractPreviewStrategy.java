package com.xddcodec.fs.framework.preview.strategy;

import com.xddcodec.fs.framework.preview.converter.IConverter;
import com.xddcodec.fs.framework.preview.core.PreviewContext;
import com.xddcodec.fs.framework.preview.core.PreviewStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;

import java.io.IOException;
import java.io.InputStream;


/**
 * 抽象预览策略（提供通用功能）
 */
@Slf4j
public abstract class AbstractPreviewStrategy implements PreviewStrategy {

    @Override
    public void fillModel(PreviewContext context, Model model) {
        // 填充基础数据
        model.addAttribute("fileName", context.getFileName());
        model.addAttribute("fileId", context.getFileId());
        model.addAttribute("fileSize", context.getFileSize());
        model.addAttribute("fileType", context.getFileType().getName());
        model.addAttribute("extension", context.getExtension());
        model.addAttribute("streamUrl", context.getStreamUrl());

        // 子类填充特定数据
        fillSpecificModel(context, model);
    }

    @Override
    public IConverter getConverter() {
        return null;
    }

    @Override
    public InputStream processStream(InputStream sourceStream, String extension) {
        IConverter converter = getConverter();
        if (converter != null) {
            return converter.convert(sourceStream, extension);
        }
        return sourceStream;
    }

    @Override
    public String getResponseExtension(String originalExtension) {
        IConverter converter = getConverter();
        if (converter != null) {
            return converter.getTargetExtension();
        }
        return originalExtension;
    }

    @Override
    public boolean supportRange() {
        return getConverter() == null;
    }

    /**
     * 子类实现：填充特定数据
     */
    protected abstract void fillSpecificModel(PreviewContext context, Model model);
}
