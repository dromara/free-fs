package com.xddcodec.fs.framework.preview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件预览配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "fs.preview")
public class FilePreviewConfig {

    /**
     * 预览文件流处理api
     */
    private String streamApi = "http://localhost:8080/api/file/stream/preview";
    /**
     * 预览文件最大大小（字节），默认500MB
     */
    private Long maxFileSize = 524288000L;

    /**
     * 单次Range请求最大大小（字节），默认10MB
     */
    private Long maxRangeSize = 10485760L;

    /**
     * 小文件直接传输阈值（字节），默认10MB
     */
    private Long smallFileSize = 10485760L;

    /**
     * 缓冲区大小（字节），默认8KB
     */
    private Integer bufferSize = 8192;

    /**
     * 浏览器不兼容媒体的转码与缓存配置。
     */
    private MediaCompatibility mediaCompatibility = new MediaCompatibility();

    @Data
    public static class MediaCompatibility {

        /** 是否启用 FFmpeg 兼容模式。 */
        private Boolean enabled = true;

        /** FFmpeg 可执行文件路径。 */
        private String ffmpegPath = "ffmpeg";

        /** 兼容媒体缓存目录。 */
        private String cacheDir = System.getProperty("java.io.tmpdir") + "/free-fs-media-cache";

        /** 同时执行的转码任务数，默认单任务以避免占满 CPU。 */
        private Integer maxConcurrentTasks = 1;

        /** 单个转码任务最长执行时间，单位秒。 */
        private Long transcodeTimeoutSeconds = 7200L;

        /** 已生成兼容媒体的保留时间，单位小时。 */
        private Long cacheTtlHours = 24L;
    }
}
