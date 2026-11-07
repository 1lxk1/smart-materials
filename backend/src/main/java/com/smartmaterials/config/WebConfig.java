package com.smartmaterials.config;

import com.smartmaterials.service.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源：把 /uploads/** 映射到本地附件目录，便于前端直接 <img src>。
 *
 * <p>SecurityConfig 里除 /api/** 外的请求都是 permitAll，因此附件可匿名读取
 * （二维码标签、报表图片等场景不需要带 token）。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService storageService;

    public WebConfig(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storageService.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
