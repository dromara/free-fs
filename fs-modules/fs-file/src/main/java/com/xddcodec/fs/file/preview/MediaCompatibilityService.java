package com.xddcodec.fs.file.preview;

import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.framework.common.enums.FileTypeEnum;
import com.xddcodec.fs.framework.preview.config.FilePreviewConfig;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 为浏览器无法直接解码的音视频生成兼容版本。
 *
 * <p>源文件从对象存储流式写入临时文件，再由 FFmpeg 转为 H.264/AAC MP4
 * 或 MP3。输出仅保存在本机临时缓存中，不会重复写入 RustFS/S3。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCompatibilityService {

    private static final int MAX_FFMPEG_LOG_BYTES = 8192;

    private final FilePreviewConfig previewConfig;
    private final ConcurrentMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    private volatile Path cacheDirectory;
    private volatile Semaphore transcodeSlots = new Semaphore(1);

    @PostConstruct
    public void initialize() {
        FilePreviewConfig.MediaCompatibility config = getConfig();
        int maxConcurrentTasks = Math.max(1, valueOrDefault(config.getMaxConcurrentTasks(), 1));
        transcodeSlots = new Semaphore(maxConcurrentTasks, true);
        cacheDirectory = Path.of(config.getCacheDir()).toAbsolutePath().normalize();

        if (isEnabled()) {
            try {
                Files.createDirectories(cacheDirectory);
            } catch (IOException e) {
                throw new IllegalStateException("无法创建媒体兼容缓存目录: " + cacheDirectory, e);
            }
        }
    }

    public boolean isSupported(FileTypeEnum fileType) {
        return fileType == FileTypeEnum.VIDEO || fileType == FileTypeEnum.AUDIO;
    }

    public CompatibleMedia prepareCompatibleMedia(
            FileInfo fileInfo,
            IStorageOperationService storage,
            FileTypeEnum fileType) {
        if (!isEnabled()) {
            throw new MediaCompatibilityException("媒体兼容模式未启用");
        }
        if (!isSupported(fileType)) {
            throw new MediaCompatibilityException("该文件类型不支持媒体兼容处理");
        }

        String targetExtension = fileType == FileTypeEnum.VIDEO ? "mp4" : "mp3";
        String cacheKey = createCacheKey(fileInfo, targetExtension);
        Path target = resolveCacheFile(cacheKey + "." + targetExtension);

        if (isUsableCache(target)) {
            touch(target);
            return new CompatibleMedia(target, targetExtension);
        }

        ReentrantLock fileLock = fileLocks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        fileLock.lock();
        try {
            if (isUsableCache(target)) {
                touch(target);
                return new CompatibleMedia(target, targetExtension);
            }
            return transcode(fileInfo, storage, fileType, cacheKey, target, targetExtension);
        } finally {
            fileLock.unlock();
            if (!fileLock.hasQueuedThreads()) {
                fileLocks.remove(cacheKey, fileLock);
            }
        }
    }

    private CompatibleMedia transcode(
            FileInfo fileInfo,
            IStorageOperationService storage,
            FileTypeEnum fileType,
            String cacheKey,
            Path target,
            String targetExtension) {
        boolean acquired = false;
        Path source = null;
        Path output = resolveCacheFile(cacheKey + ".part." + targetExtension);
        try {
            transcodeSlots.acquire();
            acquired = true;
            Files.createDirectories(cacheDirectory);
            Files.deleteIfExists(output);

            source = Files.createTempFile(
                    cacheDirectory,
                    cacheKey + "-source-",
                    normalizeSourceSuffix(fileInfo.getSuffix())
            );
            try (InputStream inputStream = storage.getFileStream(fileInfo.getObjectKey())) {
                Files.copy(inputStream, source, StandardCopyOption.REPLACE_EXISTING);
            }

            runFfmpeg(source, output, fileType, fileInfo.getId());
            if (!isUsableCache(output)) {
                throw new MediaCompatibilityException("FFmpeg 未生成有效的兼容媒体文件");
            }
            moveAtomically(output, target);
            touch(target);
            return new CompatibleMedia(target, targetExtension);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaCompatibilityException("媒体兼容处理已中断", e);
        } catch (IOException e) {
            throw new MediaCompatibilityException("媒体兼容处理失败", e);
        } finally {
            deleteQuietly(source);
            deleteQuietly(output);
            if (acquired) {
                transcodeSlots.release();
            }
        }
    }

    private void runFfmpeg(Path source, Path output, FileTypeEnum fileType, String fileId)
            throws IOException, InterruptedException {
        FilePreviewConfig.MediaCompatibility config = getConfig();
        String ffmpegPath = config.getFfmpegPath();
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            ffmpegPath = "ffmpeg";
        }

        List<String> command = new ArrayList<>(List.of(
                ffmpegPath,
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-i", source.toString()
        ));

        if (fileType == FileTypeEnum.VIDEO) {
            command.addAll(List.of(
                    "-map", "0:v:0",
                    "-map", "0:a:0?",
                    "-sn",
                    "-dn",
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "160k",
                    "-movflags", "+faststart",
                    "-map_metadata", "-1"
            ));
        } else {
            command.addAll(List.of(
                    "-map", "0:a:0",
                    "-vn",
                    "-sn",
                    "-dn",
                    "-c:a", "libmp3lame",
                    "-q:a", "4",
                    "-map_metadata", "-1"
            ));
        }
        command.add(output.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("LC_ALL", "C");
        Process process = processBuilder.start();

        ByteArrayOutputStream ffmpegLog = new ByteArrayOutputStream(MAX_FFMPEG_LOG_BYTES);
        Thread logReader = Thread.ofVirtual()
                .name("media-compatibility-ffmpeg-log")
                .start(() -> drainProcessOutput(process.getInputStream(), ffmpegLog));

        long timeoutSeconds = Math.max(1L, valueOrDefault(config.getTranscodeTimeoutSeconds(), 7200L));
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            logReader.join(1000);
            throw new MediaCompatibilityException("媒体兼容处理超时");
        }

        logReader.join(1000);
        if (process.exitValue() != 0) {
            String detail = ffmpegLog.toString(StandardCharsets.UTF_8).trim();
            log.warn("FFmpeg compatibility conversion failed, fileId={}, detail={}", fileId, detail);
            throw new MediaCompatibilityException("FFmpeg 无法转换该媒体文件");
        }
    }

    private void drainProcessOutput(InputStream inputStream, ByteArrayOutputStream target) {
        try (InputStream source = inputStream) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = source.read(buffer)) != -1) {
                int remaining = MAX_FFMPEG_LOG_BYTES - target.size();
                if (remaining > 0) {
                    target.write(buffer, 0, Math.min(read, remaining));
                }
            }
        } catch (IOException ignored) {
            // Process termination may close the stream while the log reader is draining it.
        }
    }

    private Path resolveCacheFile(String fileName) {
        Path resolved = cacheDirectory.resolve(fileName).normalize();
        if (!resolved.startsWith(cacheDirectory)) {
            throw new MediaCompatibilityException("非法的媒体缓存路径");
        }
        return resolved;
    }

    private String createCacheKey(FileInfo fileInfo, String targetExtension) {
        String source = String.join("\n",
                nullToEmpty(fileInfo.getId()),
                nullToEmpty(fileInfo.getObjectKey()),
                nullToEmpty(fileInfo.getContentMd5()),
                nullToEmpty(fileInfo.getStoragePlatformSettingId()),
                String.valueOf(fileInfo.getSize()),
                String.valueOf(fileInfo.getUpdateTime()),
                targetExtension
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", e);
        }
    }

    private String normalizeSourceSuffix(String suffix) {
        if (suffix == null) {
            return ".bin";
        }
        String normalized = suffix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.isBlank() || normalized.length() > 12) {
            return ".bin";
        }
        return "." + normalized;
    }

    private boolean isUsableCache(Path path) {
        try {
            return Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void touch(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException e) {
            log.debug("Unable to refresh media compatibility cache timestamp: {}", path);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("Unable to delete temporary media compatibility file: {}", path);
        }
    }

    @Scheduled(fixedDelayString = "${fs.preview.media-compatibility.cleanup-interval-ms:3600000}")
    public void cleanupExpiredCache() {
        if (!isEnabled() || cacheDirectory == null || !Files.isDirectory(cacheDirectory)) {
            return;
        }
        long ttlHours = valueOrDefault(getConfig().getCacheTtlHours(), 24L);
        if (ttlHours <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(ttlHours));
        try (Stream<Path> paths = Files.list(cacheDirectory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    log.debug("Unable to clean expired media compatibility cache: {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("Unable to scan media compatibility cache directory: {}", cacheDirectory);
        }
    }

    private FilePreviewConfig.MediaCompatibility getConfig() {
        return previewConfig.getMediaCompatibility();
    }

    private boolean isEnabled() {
        return Boolean.TRUE.equals(getConfig().getEnabled());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private long valueOrDefault(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }

    public record CompatibleMedia(Path path, String extension) {
    }

    public static class MediaCompatibilityException extends RuntimeException {
        public MediaCompatibilityException(String message) {
            super(message);
        }

        public MediaCompatibilityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
