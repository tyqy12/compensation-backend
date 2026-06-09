package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.mockito.ArgumentCaptor;
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
    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setActive("minio");
        properties.setMaxFileSize(10 * 1024 * 1024);
        properties.setAllowedExtensions(Arrays.asList("jpg", "jpeg", "png", "pdf", "txt"));

        FileStorageProperties.LocalStorage local = new FileStorageProperties.LocalStorage();
        local.setBasePath(tempDir.toString());
        local.setBaseUrl("http://localhost:8080/files");
        properties.setLocal(local);

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
    @DisplayName("上传到 MinIO 时不应信任客户端声明的 Content-Type")
    void uploadShouldUseStoredFileExtensionForContentType() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        doAnswer(invocation -> null).when(minioClient).putObject(any(PutObjectArgs.class));
        ReflectionTestUtils.setField(minioFileService, "minioClient", minioClient);

        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "text/html",
                "<script>alert(1)</script>".getBytes()
        );

        minioFileService.upload(file, "avatars", "avatar.jpg");

        ArgumentCaptor<PutObjectArgs> argsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(argsCaptor.capture());
        assertEquals("image/jpeg", argsCaptor.getValue().contentType());
    }

    @Test
    @DisplayName("默认上传应基于原始扩展名生成对象路径")
    void uploadWithoutCustomFileNameShouldGenerateObjectPath() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        doAnswer(invocation -> null).when(minioClient).putObject(any(PutObjectArgs.class));
        ReflectionTestUtils.setField(minioFileService, "minioClient", minioClient);

        MultipartFile file = createValidFile();

        String result = minioFileService.upload(file, "docs");

        String datePath = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertTrue(result.matches("docs/" + datePath + "/[A-Fa-f0-9]{32}\\.txt"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("Bucket 检查失败应启用本地 fallback")
    void initShouldUseLocalFallbackWhenBucketCheckFails() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("bucket unavailable"));
        MinioFileService service = new MinioFileService(properties) {
            @Override
            MinioClient createMinioClient(FileStorageProperties.MinioStorage minio) {
                return minioClient;
            }
        };

        service.init();
        String result = service.upload(createValidFile(), "docs");

        String datePath = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertTrue(result.matches("docs/" + datePath + "/[A-Fa-f0-9]{32}\\.txt"));
        assertTrue(Files.exists(tempDir.resolve(result)));
        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
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
    @DisplayName("自定义文件名改成非白名单扩展应在 fallback 前被拒绝")
    void uploadWithDisallowedCustomFileNameExtensionShouldFailBeforeFallback() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        MultipartFile file = createValidFile();

        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "docs", "invoice.exe")
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
    @DisplayName("不规范 category 或 fileKey 应在 fallback 前被拒绝")
    void unsafeSegmentPathShouldFailBeforeFallback() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        MultipartFile file = createValidFile();

        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "docs//payroll", "ok.txt")
        );
        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "docs/./payroll", "ok.txt")
        );
        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.upload(file, "docs payroll", "ok.txt")
        );
        assertThrows(IllegalArgumentException.class, () ->
                minioFileService.getUrl("docs//file.txt")
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

    @Test
    @DisplayName("getUrl 应拒绝非法 fileKey 且不调用 MinIO")
    void getUrlWithInvalidFileKeyShouldFailBeforeMinioCall() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        ReflectionTestUtils.setField(minioFileService, "minioClient", minioClient);

        assertThrows(IllegalArgumentException.class, () -> minioFileService.getUrl("../secret.txt"));

        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("getUrl 合法输入应保持 MinIO 预签名行为")
    void getUrlWithValidFileKeyShouldUseMinio() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        ReflectionTestUtils.setField(minioFileService, "minioClient", minioClient);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://127.0.0.1:9000/test-bucket/docs/file.txt");

        String url = minioFileService.getUrl("docs/file.txt");

        assertEquals("http://127.0.0.1:9000/test-bucket/docs/file.txt", url);
        ArgumentCaptor<GetPresignedObjectUrlArgs> argsCaptor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(argsCaptor.capture());
        GetPresignedObjectUrlArgs args = argsCaptor.getValue();
        assertTrue(args.extraQueryParams().get("response-content-type").contains("application/octet-stream"));
        assertTrue(args.extraQueryParams().get("response-content-disposition")
                .contains("attachment; filename=\"file.txt\""));
    }

    @Test
    @DisplayName("读写类操作应在 fallback 前拒绝非法 fileKey")
    void readOperationsWithInvalidFileKeyShouldFailBeforeFallback() {
        FileService fallback = mock(FileService.class);
        ReflectionTestUtils.setField(minioFileService, "fallbackService", fallback);

        assertThrows(IllegalArgumentException.class, () -> minioFileService.delete("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> minioFileService.getInputStream("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> minioFileService.exists("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> minioFileService.getFileSize("../secret.txt"));
        verifyNoInteractions(fallback);
    }

    private MultipartFile createValidFile() {
        byte[] content = "valid content".getBytes();
        return new MockMultipartFile("file", "file.txt", "text/plain", content);
    }
}
