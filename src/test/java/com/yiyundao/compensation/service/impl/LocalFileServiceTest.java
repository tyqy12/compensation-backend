package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("本地文件服务测试")
class LocalFileServiceTest {

    private FileService fileService;
    private FileStorageProperties properties;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        properties = new FileStorageProperties();
        properties.setActive("local");
        properties.setMaxFileSize(10 * 1024 * 1024);
        properties.setAllowedExtensions(Arrays.asList("jpg", "jpeg", "png", "pdf", "txt"));

        FileStorageProperties.LocalStorage local = new FileStorageProperties.LocalStorage();
        tempDir = Files.createTempDirectory("file-test-");
        local.setBasePath(tempDir.toString());
        local.setBaseUrl("http://localhost:8080/files");
        properties.setLocal(local);

        fileService = new LocalFileService(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
    }

    @Test
    @DisplayName("文件上传测试")
    void testUpload() {
        MultipartFile file = new MockMultipartFile(
                "test.txt",
                "test.txt",
                "text/plain",
                "Hello, World!".getBytes()
        );

        String fileKey = fileService.upload(file, "test");

        assertNotNull(fileKey);
        assertTrue(fileKey.startsWith("test/"));
        assertTrue(fileKey.endsWith(".txt"));
        assertTrue(fileService.exists(fileKey));
    }

    @Test
    @DisplayName("文件上传带自定义名称测试")
    void testUploadWithCustomName() {
        MultipartFile file = new MockMultipartFile(
                "document.pdf",
                "document.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );

        String fileKey = fileService.upload(file, "docs", "custom-name.pdf");

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertEquals("docs/" + datePath + "/custom-name.pdf", fileKey);
        assertTrue(fileService.exists(fileKey));
    }

    @Test
    @DisplayName("文件删除测试")
    void testDelete() {
        MultipartFile file = new MockMultipartFile(
                "delete-test.txt",
                "delete-test.txt",
                "text/plain",
                "Delete me".getBytes()
        );

        String fileKey = fileService.upload(file, "temp");
        assertTrue(fileService.exists(fileKey));

        fileService.delete(fileKey);
        assertFalse(fileService.exists(fileKey));
    }

    @Test
    @DisplayName("删除不存在文件测试")
    void testDeleteNonExistent() {
        assertDoesNotThrow(() -> fileService.delete("non-existent/file.txt"));
    }

    @Test
    @DisplayName("获取文件 URL 测试")
    void testGetUrl() {
        MultipartFile file = new MockMultipartFile(
                "url-test.txt",
                "url-test.txt",
                "text/plain",
                "URL test".getBytes()
        );

        String fileKey = fileService.upload(file, "test");
        String url = fileService.getUrl(fileKey);

        assertNotNull(url);
        assertTrue(url.startsWith("http://localhost:8080/files/"));
        assertTrue(url.endsWith(fileKey));
    }

    @Test
    @DisplayName("检查文件是否存在测试")
    void testExists() {
        MultipartFile file = new MockMultipartFile(
                "exists-test.txt",
                "exists-test.txt",
                "text/plain",
                "Exists test".getBytes()
        );

        String fileKey = fileService.upload(file, "test");
        assertTrue(fileService.exists(fileKey));

        assertFalse(fileService.exists("non-existent-path/file.txt"));
    }

    @Test
    @DisplayName("获取文件大小测试")
    void testGetFileSize() {
        String content = "File size test content";
        MultipartFile file = new MockMultipartFile(
                "size-test.txt",
                "size-test.txt",
                "text/plain",
                content.getBytes()
        );

        String fileKey = fileService.upload(file, "test");
        long size = fileService.getFileSize(fileKey);

        assertEquals(content.length(), size);
    }

    @Test
    @DisplayName("获取不存在文件大小测试")
    void testGetFileSizeNonExistent() {
        long size = fileService.getFileSize("non-existent/file.txt");
        assertEquals(-1, size);
    }

    @Test
    @DisplayName("获取文件输入流测试")
    void testGetInputStream() throws IOException {
        String content = "Stream test content";
        MultipartFile file = new MockMultipartFile(
                "stream-test.txt",
                "stream-test.txt",
                "text/plain",
                content.getBytes()
        );

        String fileKey = fileService.upload(file, "test");
        InputStream inputStream = fileService.getInputStream(fileKey);

        assertNotNull(inputStream);
        String readContent = new String(inputStream.readAllBytes());
        assertEquals(content, readContent);
    }

    @Test
    @DisplayName("文件上传 - 空文件测试")
    void testUploadEmptyFile() {
        MultipartFile file = new MockMultipartFile(
                "empty.txt",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () ->
                fileService.upload(file, "test")
        );
    }

    @Test
    @DisplayName("文件上传 - 不支持的文件类型测试")
    void testUploadUnsupportedType() {
        MultipartFile file = new MockMultipartFile(
                "test.exe",
                "test.exe",
                "application/octet-stream",
                "exe content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () ->
                fileService.upload(file, "test")
        );
    }

    @Test
    @DisplayName("文件上传 - 文件大小超限测试")
    void testUploadFileSizeExceeded() {
        properties.setMaxFileSize(10); // 10 bytes

        byte[] largeContent = new byte[20];
        MultipartFile file = new MockMultipartFile(
                "large.txt",
                "large.txt",
                "text/plain",
                largeContent
        );

        assertThrows(IllegalArgumentException.class, () ->
                fileService.upload(file, "test")
        );
    }

    @Test
    @DisplayName("多级目录创建测试")
    void testMultiLevelDirectory() {
        MultipartFile file = new MockMultipartFile(
                "nested.txt",
                "nested.txt",
                "text/plain",
                "Nested content".getBytes()
        );

        String fileKey = fileService.upload(file, "a/b/c/deeply");

        assertTrue(fileKey.startsWith("a/b/c/deeply/"));
        assertTrue(fileService.exists(fileKey));
    }

    @Test
    @DisplayName("不同分类文件隔离测试")
    void testCategoryIsolation() {
        MultipartFile file1 = new MockMultipartFile(
                "category1.txt",
                "category1.txt",
                "text/plain",
                "Category 1".getBytes()
        );

        MultipartFile file2 = new MockMultipartFile(
                "category2.txt",
                "category2.txt",
                "text/plain",
                "Category 2".getBytes()
        );

        String key1 = fileService.upload(file1, "category1");
        String key2 = fileService.upload(file2, "category2");

        assertTrue(key1.startsWith("category1/"));
        assertTrue(key2.startsWith("category2/"));
        assertNotEquals(key1, key2);
    }
}
