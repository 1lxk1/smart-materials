package com.smartmaterials.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * 全局 CORS 配置。
 *
 * <p>前后端分离部署（Vercel 前端 + Render 后端）时，浏览器会跨域请求，
 * 这里统一放行；生产环境建议用环境变量 CORS_ALLOWED_ORIGINS 收窄到自己的前端域名。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsConfiguration config = buildConfiguration();
        registry.addMapping("/**")
                .allowedOriginPatterns(config.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** Spring Security 的 cors() 会用这个 Bean，两处保持同一套规则。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfiguration());
        return source;
    }

    private CorsConfiguration buildConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(appProperties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.isEmpty() || origins.contains("*")) {
            // allowedOriginPatterns 才允许与 allowCredentials=true 共存
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOriginPatterns(origins);
            config.setAllowedOrigins(origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
}
