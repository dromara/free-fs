package com.xddcodec.fs.file.controller;

import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.preview.ArchiveFilePreviewService;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.enums.FileTypeEnum;
import com.xddcodec.fs.framework.preview.config.FilePreviewConfig;
import com.xddcodec.fs.framework.preview.core.PreviewStrategy;
import com.xddcodec.fs.framework.preview.factory.PreviewStrategyManager;
import com.xddcodec.fs.framework.preview.strategy.impl.archive.ArchiveUtil;
import com.xddcodec.fs.storage.facade.StorageServiceFacade;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件流控制器
 * 
 * @author xddcode
 */
@Slf4j
@RestController
@RequestMapping("/api/file/stream")
@RequiredArgsConstructor
public class FileStreamController {

    private final FileInfoService fileInfoService;
    private final StorageServiceFacade storageServiceFacade;
    private final FilePreviewConfig previewConfig;
    private final PreviewStrategyManager strategyManager;
    private final ArchiveFilePreviewService archiveFilePreviewService;

    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");

    @GetMapping("/preview/{fileId}")
    public ResponseEntity<StreamingResponseBody> preview(
            @PathVariable String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        FileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }

        IStorageOperationService storage = storageServiceFacade
                .getStorageService(fileInfo.getStoragePlatformSettingId());

        FileTypeEnum fileType = FileTypeEnum.fromFileName(fileInfo.getDisplayName());
        PreviewStrategy strategy = strategyManager.getStrategy(fileType);

        log.info("文件: {}, 类型: {}, 匹配策略: {}", fileInfo.getDisplayName(), fileType, strategy.getClass().getSimpleName());

        // 修复逻辑：如果策略不支持Range（说明是转换流，如Docx转PDF），则强制走FullRequest
        // 即使前端传了Range头也不处理，防止截断
        if (!strategy.supportRange() || rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return handleFullRequest(storage, fileInfo, strategy);
        }

        return handleRangeRequest(storage, fileInfo, strategy, rangeHeader);
    }

    private ResponseEntity<StreamingResponseBody> handleFullRequest(
            IStorageOperationService storage, FileInfo fileInfo, PreviewStrategy strategy) {

        StreamingResponseBody stream = outputStream -> {
            try (InputStream sourceStream = storage.getFileStream(fileInfo.getObjectKey());
                 InputStream processedStream = strategy.processStream(sourceStream, fileInfo.getSuffix())) {

                copyStream(processedStream, outputStream);

            } catch (IOException e) {
                log.debug("文件流传输中断: {}", fileInfo.getDisplayName());
            }
        };

        // 传入 fileInfo.getSize() 仅作为参考，buildHeaders 内部决定是否使用
        HttpHeaders headers = buildHeaders(fileInfo, strategy, fileInfo.getSize(), false);
        return ResponseEntity.ok().headers(headers).body(stream);
    }

    private ResponseEntity<StreamingResponseBody> handleRangeRequest(
            IStorageOperationService storage, FileInfo fileInfo,
            PreviewStrategy strategy, String rangeHeader) {

        long fileSize = fileInfo.getSize();
        long start = 0;
        long end = fileSize - 1;

        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
        if (matcher.matches()) {
            String startGroup = matcher.group(1);
            String endGroup = matcher.group(2);
            if (!startGroup.isEmpty()) start = Long.parseLong(startGroup);
            if (!endGroup.isEmpty()) end = Math.min(Long.parseLong(endGroup), fileSize - 1);
        }

        long maxRangeSize = previewConfig.getMaxRangeSize();
        if (end - start + 1 > maxRangeSize) {
            end = start + maxRangeSize - 1;
        }

        final long finalStart = start;
        final long finalEnd = end;
        final long contentLength = finalEnd - finalStart + 1;

        StreamingResponseBody stream = outputStream -> {
            try (InputStream inputStream = storage.downloadFileRange(
                    fileInfo.getObjectKey(), finalStart, finalEnd)) {
                copyStreamLimited(inputStream, outputStream, contentLength);
            } catch (IOException e) {
                log.debug("Range流传输中断: {}", fileInfo.getDisplayName());
            }
        };

        HttpHeaders headers = buildHeaders(fileInfo, strategy, contentLength, true);
        headers.add(HttpHeaders.CONTENT_RANGE,
                String.format("bytes %d-%d/%d", finalStart, finalEnd, fileSize));

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(stream);
    }

    /**
     * 获取压缩包内文件流
     */
    @GetMapping("/preview/archive/inner/{tempId}")
    public ResponseEntity<StreamingResponseBody> previewArchiveInner(@PathVariable String tempId) {
        log.info("获取压缩包内文件流: tempId={}", tempId);

        byte[] fileContent = archiveFilePreviewService.getCachedInnerFile(tempId);
        if (fileContent == null) {
            log.warn("压缩包内文件缓存已过期或不存在: tempId={}", tempId);
            return ResponseEntity.notFound().build();
        }

        String displayName = archiveFilePreviewService.getCachedInnerFileName(tempId);
        if (displayName == null || displayName.isBlank()) {
            displayName = "file.bin";
        }

        String suffix = ArchiveUtil.getExtension(displayName).toLowerCase(Locale.ROOT);
        FileTypeEnum fileType = FileTypeEnum.fromFileName(displayName);
        PreviewStrategy strategy = strategyManager.getStrategy(fileType);

        StreamingResponseBody stream = outputStream -> {
            try (InputStream sourceStream = new ByteArrayInputStream(fileContent);
                 InputStream processedStream = strategy.processStream(sourceStream, suffix)) {
                copyStream(processedStream, outputStream);
            } catch (IOException e) {
                log.debug("压缩包内文件流传输中断: tempId={}", tempId);
            }
        };

        HttpHeaders headers = buildArchiveInnerStreamHeaders(
                strategy, displayName, suffix, fileContent.length, false);
        return ResponseEntity.ok().headers(headers).body(stream);
    }

    /**
     * 与 {@link #buildHeaders} 逻辑对齐：需转换的类型（如 Word/PPT）不设置 Content-Length，响应 PDF。
     */
    private HttpHeaders buildArchiveInnerStreamHeaders(PreviewStrategy strategy, String displayName,
            String originalSuffix, long sourceByteLength, boolean isRange) {
        HttpHeaders headers = new HttpHeaders();

        String responseExtension = strategy.getResponseExtension(originalSuffix);
        String fileName = changeExtension(displayName, responseExtension);

        headers.setContentType(MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM));

        if (isRange || !strategy.needConvert()) {
            headers.setContentLength(sourceByteLength);
        }

        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename*=UTF-8''" + encodeFileName(fileName));

        if (strategy.supportRange()) {
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setCacheControl("public, max-age=604800");
        } else {
            headers.set(HttpHeaders.ACCEPT_RANGES, "none");
            headers.setCacheControl("no-cache");
        }

        return headers;
    }

    /**
     * 构建响应头
     */
    private HttpHeaders buildHeaders(FileInfo file, PreviewStrategy strategy,
                                     long visibleLength, boolean isRange) {
        HttpHeaders headers = new HttpHeaders();

        String responseExtension = strategy.getResponseExtension(file.getSuffix());
        String fileName = changeExtension(file.getDisplayName(), responseExtension);

        // 设置 Content-Type
        headers.setContentType(MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM));

        // 智能设置 Content-Length：转换流不设置长度，Range请求必须设置
        if (isRange || !strategy.needConvert()) {
            headers.setContentLength(visibleLength);
        }

        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename*=UTF-8''" + encodeFileName(fileName));

        if (strategy.supportRange()) {
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setCacheControl("public, max-age=604800");
        } else {
            headers.set(HttpHeaders.ACCEPT_RANGES, "none");
            headers.setCacheControl("no-cache");
        }

        return headers;
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[previewConfig.getBufferSize()];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        out.flush();
    }

    private void copyStreamLimited(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[previewConfig.getBufferSize()];
        long totalRead = 0;
        int bytesRead;

        while (totalRead < limit) {
            int toRead = (int) Math.min(buffer.length, limit - totalRead);
            bytesRead = in.read(buffer, 0, toRead);
            if (bytesRead == -1) break;
            out.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
        }
        out.flush();
    }

    private String changeExtension(String fileName, String newExtension) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return fileName + "." + newExtension;
        String originalExtension = fileName.substring(dotIndex + 1);
        if (originalExtension.equalsIgnoreCase(newExtension)) return fileName;
        return fileName.substring(0, dotIndex) + "." + newExtension;
    }

    private String encodeFileName(String name) {
        try {
            return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
