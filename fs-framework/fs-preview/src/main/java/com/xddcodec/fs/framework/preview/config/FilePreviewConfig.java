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
}

