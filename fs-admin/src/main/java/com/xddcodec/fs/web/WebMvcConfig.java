package com.xddcodec.fs.web;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.xddcodec.fs.framework.security.properties.SecurityProperties;
import com.xddcodec.fs.interceptor.PreviewInterceptor;
import com.xddcodec.fs.interceptor.StoragePlatformInterceptor;
import com.xddcodec.fs.interceptor.WorkspaceInterceptor;
import com.xddcodec.fs.storage.plugin.local.config.LocalStorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置
 *
 * @Author: xddcode
 * @Date: 2024/11/18 13:53
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private StoragePlatformInterceptor storagePlatformInterceptor;

    @Autowired
    private PreviewInterceptor previewInterceptor;

    @Autowired
    private WorkspaceInterceptor workspaceInterceptor;

    @Autowired
    private LocalStorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String baseUrl = storageProperties.getBaseUrl();
        // 提取最后一个 / 之后的内容
        String prefix = baseUrl.substring(baseUrl.lastIndexOf("/") + 1);

        registry.addResourceHandler("/" + prefix + "/**")
                .addResourceLocations("file:" + storageProperties.getBasePath() + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] previewTokenPaths = {"/preview/token/**", "/archive/preview/token/**"};

        // 工作空间上下文拦截器（在登录校验之后）
        registry.addInterceptor(workspaceInterceptor)
                .addPathPatterns("/apis/**")
                .addPathPatterns(previewTokenPaths)
                .order(1);

        // Sa-Token 登录校验拦截器
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(securityProperties.getPathPattern())
                .excludePathPatterns(securityProperties.getExcludes())
                .order(2);

        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(previewTokenPaths)
                .order(2);


        //注册存储平台切换拦截器
        registry.addInterceptor(storagePlatformInterceptor)
                .addPathPatterns(securityProperties.getPathPattern())
                .excludePathPatterns(securityProperties.getExcludes())
                .order(3);

        registry.addInterceptor(storagePlatformInterceptor)
                .addPathPatterns(previewTokenPaths)
                .order(3);

        //注册文件预览防盗链拦截器
        registry.addInterceptor(previewInterceptor)
                .addPathPatterns("/preview/**", "/archive/preview/**", "/api/file/stream/preview/archive/inner/**")
                .excludePathPatterns("/preview/token/**", "/preview/error", "/archive/preview/token/**")
                .order(4);
    }
}
