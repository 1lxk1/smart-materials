package com.smartmaterials;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 材智通 · 智慧材料库数字化管理系统 —— 后端入口。
 *
 * <p>技术栈：Spring Boot 3 + MyBatis-Plus + PostgreSQL 15 + Spring Security/JWT。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.smartmaterials.mapper")
public class SmartMaterialsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartMaterialsApplication.class, args);
    }
}
