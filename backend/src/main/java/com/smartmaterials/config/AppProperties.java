package com.smartmaterials.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用级配置（JWT / CORS / 初始化开关）。 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Init init = new Init();
    private final Upload upload = new Upload();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Init getInit() {
        return init;
    }

    public Upload getUpload() {
        return upload;
    }

    public static class Jwt {
        private String secret = "change-me-smart-materials-jwt-secret-key-32bytes-minimum";
        private long expireMinutes = 720;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpireMinutes() {
            return expireMinutes;
        }

        public void setExpireMinutes(long expireMinutes) {
            this.expireMinutes = expireMinutes;
        }
    }

    public static class Cors {
        /** 逗号分隔，* 表示放开全部来源。 */
        private String allowedOrigins = "*";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Init {
        private boolean seed = true;

        public boolean isSeed() {
            return seed;
        }

        public void setSeed(boolean seed) {
            this.seed = seed;
        }
    }

    /** 附件（损耗照片等）本地存储。生产可用 UPLOAD_DIR 指向持久盘 / 对象存储挂载点。 */
    public static class Upload {
        /** 存储根目录，相对路径按进程工作目录解析。 */
        private String dir = "uploads";
        /** 单文件大小上限（字节），默认 10MB，需 ≤ spring.servlet.multipart.max-file-size。 */
        private long maxSizeBytes = 10L * 1024 * 1024;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(long maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }
    }
}
