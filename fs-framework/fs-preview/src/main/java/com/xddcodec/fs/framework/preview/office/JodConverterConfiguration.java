package com.xddcodec.fs.framework.preview.office;

import com.xddcodec.fs.framework.preview.converter.impl.OfficeToPdfConverter;
import com.xddcodec.fs.framework.preview.queue.OfficeTaskQueueHandler;
import com.xddcodec.fs.framework.preview.strategy.impl.OfficePreviewStrategy;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fs.preview.office", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JodConverterConfiguration {

    private final OfficeToPdfConfig config;

    private OfficeManager officeManager;

    @Bean
    public OfficeManager officeManager() {
        // 调用路径解析
        String resolvedOfficeHome = LibreOfficePathResolver.resolveOfficeHome(config.getOfficeHome());

        // 添加路径验证逻辑
        if (!LibreOfficePathResolver.validateOfficeHome(resolvedOfficeHome)) {
            OSType osType = LibreOfficePathResolver.detectOperatingSystem();
            String installGuide = getInstallGuideMessage(osType);
            log.error("========== LibreOffice 启动失败 ==========");
            log.error("✗ LibreOffice 路径无效: {}", resolvedOfficeHome);
            log.error("{}", installGuide);
            log.error("==========================================");
            throw new IllegalStateException("LibreOffice 未正确配置，请检查路径或安装 LibreOffice。" + installGuide);
        }

        // 自动创建工作目录
        File workingDir = new File(config.getCachePath());
        if (!workingDir.exists()) {
            boolean created = workingDir.mkdirs();
            if (!created) {
                throw new IllegalStateException("无法创建工作目录: " + config.getCachePath());
            }
            log.info("创建工作目录: {}", workingDir.getAbsolutePath());
        }

        // 使用解析后的路径构建 OfficeManager
        LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
                .officeHome(resolvedOfficeHome)  // 使用解析后的路径
                .taskExecutionTimeout(config.getTaskExecutionTimeout())
                .taskQueueTimeout(config.getTaskQueueTimeout())
                .maxTasksPerProcess(config.getMaxTasksPerProcess())
                .workingDir(new File(config.getCachePath()));
        officeManager = builder.build();

        try {
            officeManager.start();
            log.info("========== LibreOffice 启动成功 ==========");
            log.info("✓ Office 文档预览功能已启用");
            log.info("  LibreOffice 路径: {}", resolvedOfficeHome);
            log.info("  工作目录: {}", config.getCachePath());
            log.info("==========================================");
        } catch (Exception e) {
            log.error("========== LibreOffice 启动失败 ==========");
            log.error("✗ LibreOffice 进程池启动失败");
            log.error("  路径: {}", resolvedOfficeHome);
            log.error("  错误: {}", e.getMessage());
            log.error("");
            log.error("常见问题排查提示:");
            log.error("  1. 检查 LibreOffice 路径是否正确");
            log.error("  2. 检查是否有足够的文件系统权限");
            log.error("  3. 检查端口是否被占用（默认端口: 2002）");
            log.error("  4. 检查 LibreOffice 是否已正确安装");
            log.error("==========================================");
            throw new RuntimeException("LibreOffice 启动失败，请检查配置和日志", e);
        }

        return officeManager;
    }

    /**
     * 根据操作系统类型获取安装指引消息
     *
     * @param osType 操作系统类型
     * @return 安装指引消息
     */
    private String getInstallGuideMessage(OSType osType) {
        return switch (osType) {
            case WINDOWS -> "\n请确保 LibreOffice 已正确安装。" +
                    "\n下载地址: https://www.libreoffice.org/download/download/" +
                    "\n或在配置文件中指定正确的 LibreOffice 路径: fs.preview.office.office-home";
            case LINUX -> "\n请使用以下命令安装 LibreOffice:" +
                    "\nUbuntu/Debian: sudo apt-get install libreoffice" +
                    "\nCentOS/RHEL: sudo yum install libreoffice" +
                    "\nArch Linux: sudo pacman -S libreoffice-fresh" +
                    "\n或在配置文件中指定正确的 LibreOffice 路径: fs.preview.office.office-home";
            case MAC -> "\n请访问 https://www.libreoffice.org/download/download/ 下载并安装 LibreOffice" +
                    "\n或在配置文件中指定正确的 LibreOffice 路径: fs.preview.office.office-home";
            default -> "\n请安装 LibreOffice 或在配置文件中指定正确的路径: fs.preview.office.office-home";
        };
    }

    @Bean
    public OfficeToPdfConverter officeToPdfConverter(
            OfficeToPdfConfig config,
            OfficeTaskQueueHandler officeTaskQueueHandler) {

        return new OfficeToPdfConverter(config, officeTaskQueueHandler);
    }

    @Bean
    public OfficePreviewStrategy officePreviewStrategy(OfficeToPdfConverter officeToPdfConverter) {
        return new OfficePreviewStrategy(officeToPdfConverter);
    }

    /**
     * 确保项目关闭时，LibreOffice 也能关闭
     */
    @PreDestroy
    public void destroy() {
        if (officeManager != null && officeManager.isRunning()) {
            log.info("正在关闭 LibreOffice 进程...");
            try {
                officeManager.stop();
            } catch (Exception e) {
                log.error("关闭 LibreOffice 异常", e);
            }
        }
    }
}
