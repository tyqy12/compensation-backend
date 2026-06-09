package com.yiyundao.compensation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "file-storage")
public class FileStorageProperties {

    private String active = "local";

    private LocalStorage local = new LocalStorage();
    private MinioStorage minio = new MinioStorage();

    private long maxFileSize = 10 * 1024 * 1024;

    private java.util.List<String> allowedExtensions = java.util.Arrays.asList(
            "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "zip"
    );

    @Data
    public static class LocalStorage {
        private String basePath = "/data/files";
        private String baseUrl = "/api/v1/files/download";
    }

    @Data
    public static class MinioStorage {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }
}
