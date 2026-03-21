package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MinIO 文件服务参数校验测试")
class MinioFileServiceTest {

    private FileStorageProperties properties;
    private MinioFileService minioFileService;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setActive("minio");
        properties.setMaxFileSize(10 * 1024 * 1024);
        properties.setAllowedExtensions(Arrays.asList("jpg", "jpeg", "png", "pdf", "txt"));

        FileStorageProperties.MinioStorage minio = new FileStorageProperties.MinioStorage();
        minio.setBucket("test-bucket");
        minio.setEndpoint("http://127.0.0.1:9000");
        minio.setAccessKey("test-access");
        minio.setSecretKey("test-secret");
        properties.setMinio(minio);

        minioFileService = new MinioFileService(properties);
    }

    @Test
    @DisplayName("合法输入应上传成功并生成对象路径")
    void uploadWithValidInputShouldSucceed() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        doAnswer(invocation -> null).when(minioClient).putObject(any(PutObjectArgs.class));
        ReflectionTestUtils.setField(minioFileService, "minioClient", minioClient);

        MultipartFile file = new MockMultipartFile(
                "test.txt",
                "test.txt",
                "text/plain",
                "Hello MinIO".getBytes()
        );

        String result = minioFileService.upload(file, "docs", "contract.txt");

        assertEquals("docs/" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/contract.txt", result);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("包含 .. 的分类应被拒绝（fallback 前也应校验）")
    void uploadWithDotDotCategoryShouldFailBeforeFallback() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        MultipartFile file = createValidFile();

        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "../private", "ok.txt")
        );
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("包含 / 或 \\\\ 的自定义文件名应被拒绝（fallback 前也应校验）")
    void uploadWithSlashOrBackslashInCustomFileNameShouldFailBeforeFallback() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        MultipartFile file = createValidFile();

        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "docs", "a/b.txt")
        );
        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "docs", "a\\b.txt")
        );
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("空 category 应被拒绝（fallback 前也应校验）")
    void uploadWithBlankCategoryShouldFailBeforeFallback() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        MultipartFile file = createValidFile();

        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, " ", "ok.txt")
        );
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("fallback 存在时合法输入应保持委托行为")
    void uploadWithFallbackShouldDelegateWhenInputIsValid() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        MultipartFile file = createValidFile();
        when(fallback.upload(file, "docs", "ok.txt")).thenReturn("docs/2026/03/21/ok.txt");

        String result = minioFileService.upload(file, "docs", "ok.txt");

        assertEquals("docs/2026/03/21/ok.txt", result);
        verify(fallback).upload(file, "docs", "ok.txt");
    }

    private MultipartFile createValidFile() {
        byte[] content = "valid content".getBytes();
        return new MockMultipartFile("file", "file.txt", "text/plain", content);
    }
}
