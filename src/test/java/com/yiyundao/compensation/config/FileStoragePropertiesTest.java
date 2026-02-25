package com.yiyundao.compensation.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("文件存储配置测试")
class FileStoragePropertiesTest {

    @Test
    @DisplayName("默认配置测试")
    void testDefaultConfig() {
        FileStorageProperties properties = new FileStorageProperties();

        assertEquals("local", properties.getActive());
        assertEquals(10 * 1024 * 1024, properties.getMaxFileSize());
        assertNotNull(properties.getLocal());
        assertNotNull(properties.getMinio());
        assertNotNull(properties.getAllowedExtensions());
    }

    @Test
    @DisplayName("允许的文件扩展名测试")
    void testAllowedExtensions() {
        FileStorageProperties properties = new FileStorageProperties();

        assertTrue(properties.getAllowedExtensions().contains("jpg"));
        assertTrue(properties.getAllowedExtensions().contains("jpeg"));
        assertTrue(properties.getAllowedExtensions().contains("png"));
        assertTrue(properties.getAllowedExtensions().contains("gif"));
        assertTrue(properties.getAllowedExtensions().contains("pdf"));
        assertTrue(properties.getAllowedExtensions().contains("doc"));
        assertTrue(properties.getAllowedExtensions().contains("docx"));
        assertTrue(properties.getAllowedExtensions().contains("xls"));
        assertTrue(properties.getAllowedExtensions().contains("xlsx"));
        assertTrue(properties.getAllowedExtensions().contains("zip"));
    }

    @Test
    @DisplayName("本地存储配置测试")
    void testLocalStorageConfig() {
        FileStorageProperties.LocalStorage local = new FileStorageProperties.LocalStorage();
        local.setBasePath("/custom/files");
        local.setBaseUrl("http://custom.example.com/files");

        assertEquals("/custom/files", local.getBasePath());
        assertEquals("http://custom.example.com/files", local.getBaseUrl());
    }

    @Test
    @DisplayName("MinIO 存储配置测试")
    void testMinioStorageConfig() {
        FileStorageProperties.MinioStorage minio = new FileStorageProperties.MinioStorage();
        minio.setEndpoint("http://minio.example.com:9000");
        minio.setAccessKey("minioadmin");
        minio.setSecretKey("miniosecret");
        minio.setBucket("my-bucket");

        assertEquals("http://minio.example.com:9000", minio.getEndpoint());
        assertEquals("minioadmin", minio.getAccessKey());
        assertEquals("miniosecret", minio.getSecretKey());
        assertEquals("my-bucket", minio.getBucket());
    }

    @Test
    @DisplayName("配置覆盖测试")
    void testConfigOverride() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setActive("minio");
        properties.setMaxFileSize(20 * 1024 * 1024);

        assertEquals("minio", properties.getActive());
        assertEquals(20 * 1024 * 1024, properties.getMaxFileSize());
    }

    @Test
    @DisplayName("文件扩展名包含大写测试")
    void testUpperCaseExtensions() {
        FileStorageProperties properties = new FileStorageProperties();

        assertFalse(properties.getAllowedExtensions().contains("JPG"));
        assertFalse(properties.getAllowedExtensions().contains("PDF"));
    }
}
