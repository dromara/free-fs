package com.xddcodec.fs.file.preview;

import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.constant.RedisKey;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.enums.FileTypeEnum;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.preview.config.FilePreviewConfig;
import com.xddcodec.fs.framework.preview.core.PreviewContext;
import com.xddcodec.fs.framework.preview.core.PreviewStrategy;
import com.xddcodec.fs.framework.preview.factory.PreviewStrategyManager;
import com.xddcodec.fs.framework.preview.strategy.impl.archive.ArchiveUtil;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 压缩包内文件预览服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveFilePreviewService {

    private final FileInfoService fileInfoService;
    private final PreviewStrategyManager strategyManager;
    private final FilePreviewConfig previewConfig;
    private final RedisRepository redisRepository;

    /**
     * 缓存的压缩包内文件内容的键前缀
     */
    private static final String ARCHIVE_INNER_FILE_KEY_PREFIX = "archive:inner:file:";
    /**
     * 与内层文件流配套的原始文件名（用于后缀识别与 Office 转 PDF 等）
     */
    private static final String ARCHIVE_INNER_META_KEY_PREFIX = "archive:inner:meta:";
    /**
     * 缓存有效期，单位秒
     */
    private static final long CACHE_EXPIRE_SECONDS = 5 * 60; // 5分钟

    /**
     * 预览压缩包内的文件
     */
    public String previewInnerFile(String archiveFileId, String innerPath, Model model) {
        if (!ArchiveUtil.isSafeArchiveInnerPath(innerPath)) {
            return buildErrorPage(model, I18nUtils.getMessage("archive.path.invalid"), 
                    I18nUtils.getMessage("archive.path.illegal"));
        }

        byte[] archiveBytes = getCachedInnerFile(archiveFileId);
        if (archiveBytes == null) {
            FileInfo archiveFile = fileInfoService.getById(archiveFileId);
            if (archiveFile == null) {
                return buildErrorPage(model, I18nUtils.getMessage("archive.not.exist"), 
                        I18nUtils.getMessage("archive.file.not.exist.or.deleted"));
            }
            try {
                archiveBytes = downloadFile(archiveFile.getId());
            } catch (Exception e) {
                return buildErrorPage(model, I18nUtils.getMessage("archive.download.failed"), 
                        I18nUtils.getMessage("archive.content.unavailable"));
            }
        }

        try {
            // 3. 从提取出的字节流中再次提取目标文件 (innerPath)
            byte[] fileContent = extractFileFromBytes(archiveBytes, innerPath);

            if (fileContent == null) {
                return buildErrorPage(model, I18nUtils.getMessage("archive.file.not.found"), 
                        I18nUtils.getMessage("archive.file.not.found.in.archive"));
            }

            Long maxFileSize = previewConfig.getMaxFileSize();
            if (maxFileSize != null && maxFileSize > 0 && fileContent.length > maxFileSize) {
                return buildErrorPage(model, I18nUtils.getMessage("file.too.large"),
                        I18nUtils.getMessage("file.size.limit.exceeded", 
                                new Object[]{maxFileSize / 1024 / 1024}));
            }

            // 获取文件名
            String fileName = ArchiveUtil.getFileNameFromPath(innerPath);
            FileTypeEnum fileType = FileTypeEnum.fromFileName(fileName);

            // 获取预览策略
            PreviewStrategy strategy = strategyManager.getStrategy(fileType);

            // 生成临时ID并缓存文件内容（使用 Base64 编码避免序列化问题）
            String tempId = UUID.randomUUID().toString().replace("-", "");
            String cacheKey = ARCHIVE_INNER_FILE_KEY_PREFIX + tempId;
            String base64Content = java.util.Base64.getEncoder().encodeToString(fileContent);
            redisRepository.setExpire(cacheKey, base64Content, CACHE_EXPIRE_SECONDS);
            redisRepository.setExpire(ARCHIVE_INNER_META_KEY_PREFIX + tempId, fileName, CACHE_EXPIRE_SECONDS);

            // 每次内层预览单独签发流 token，避免共用 previewToken+:stream 覆盖 Redis 导致多窗口 403/串文件
            String streamAccessToken = UUID.randomUUID().toString().replace("-", "");
            redisRepository.setExpire(RedisKey.getPreviewTokenKey(streamAccessToken), tempId, CACHE_EXPIRE_SECONDS);
            redisRepository.setExpire(
                    RedisKey.getPreviewWorkspaceKey(streamAccessToken),
                    WorkspaceContext.getWorkspaceId(),
                    CACHE_EXPIRE_SECONDS
            );

            String streamUrl = "/api/file/stream/preview/archive/inner/" + tempId
                    + "?previewToken=" + streamAccessToken;

            PreviewContext context = PreviewContext.builder()
                    .fileId(tempId)
                    .fileName(fileName)
                    .streamUrl(streamUrl)
                    .fileSize((long) fileContent.length)
                    .extension(ArchiveUtil.getExtension(fileName))
                    .fileType(fileType)
                    .build();

            strategy.fillModel(context, model);
            return strategy.getTemplatePath();

        } catch (Exception e) {
            log.error("提取压缩包内文件失败: archiveFileId={}, innerPath={}",
                    archiveFileId, innerPath, e);
            return buildErrorPage(model, I18nUtils.getMessage("archive.preview.failed"), e.getMessage());
        }
    }

    /**
     * 辅助：下载方法
     */
    private byte[] downloadFile(String fileId) throws Exception {
        String streamUrl = previewConfig.getStreamApi() + "/" + fileId;
        try (InputStream is = new BufferedInputStream(URI.create(streamUrl).toURL().openStream())) {
            return is.readAllBytes(); // 注意：如果原始文件极大，建议改用临时文件引用而非内存数组
        }
    }

    /**
     * 获取缓存的压缩包内文件内容
     */
    public byte[] getCachedInnerFile(String tempId) {
        String cacheKey = ARCHIVE_INNER_FILE_KEY_PREFIX + tempId;
        Object cached = redisRepository.get(cacheKey);
        if (cached == null) {
            return null;
        }

        // 如果是 String 类型（Base64 编码），需要解码
        if (cached instanceof String) {
            try {
                return java.util.Base64.getDecoder().decode((String) cached);
            } catch (Exception e) {
                log.error("解码 Base64 失败: tempId={}", tempId, e);
                return null;
            }
        }

        // 如果是 byte[] 类型，直接返回
        if (cached instanceof byte[]) {
            return (byte[]) cached;
        }

        log.error("缓存数据类型不正确: tempId={}, type={}", tempId, cached.getClass().getName());
        return null;
    }

    /**
     * 获取压缩包内临时流对应的原始文件名（含扩展名）
     */
    public String getCachedInnerFileName(String tempId) {
        Object cached = redisRepository.get(ARCHIVE_INNER_META_KEY_PREFIX + tempId);
        return cached == null ? null : String.valueOf(cached);
    }

    /**
     * 核心：支持从字节数组中提取内部文件（实现递归预览的关键）
     */
    private byte[] extractFileFromBytes(byte[] archiveData, String targetPath) throws Exception {
        Path tempFile = Files.createTempFile("nest_archive_", ".tmp");
        try {
            Files.write(tempFile, archiveData);

            try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "r");
                 IInArchive inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(raf))) {

                ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();
                for (ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                    // 同样应用乱码修复
                    String currentPath = ArchiveUtil.normalizePath(ArchiveUtil.fixFileNameEncoding(item.getPath()));
                    String target = ArchiveUtil.normalizePath(targetPath);

                    if (currentPath.equals(target) && !item.isFolder()) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        item.extractSlow(data -> {
                            baos.write(data, 0, data.length);
                            return data.length;
                        });
                        return baos.toByteArray();
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
        return null;
    }

    private String buildErrorPage(Model model, String errorMessage, String errorDetail) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("errorDetail", errorDetail);
        return "preview/error";
    }
}
