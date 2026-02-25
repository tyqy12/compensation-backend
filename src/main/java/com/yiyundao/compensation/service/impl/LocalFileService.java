package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file-storage.active", havingValue = "local", matchIfMissing = true)
public class LocalFileService implements FileService {

    private final FileStorageProperties properties;
    private Path rootLocation;

    @PostConstruct
    public void init() {
        if (this.rootLocation != null) {
            return;
        }
        this.rootLocation = Paths.get(properties.getLocal().getBasePath());
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录", e);
        }
    }

    @Override
    public String upload(MultipartFile file, String category) {
        return upload(file, category, null);
    }

    @Override
    public String upload(MultipartFile file, String category, String fileName) {
        FileUploadValidator.validate(file, properties);
        validateCategory(category);
        validateCustomFileName(fileName);

        if (fileName == null || fileName.isEmpty()) {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            fileName = generateFileName(extension);
        }

        try {
            ensureInitialized();
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String relativePath = category + "/" + datePath + "/" + fileName;
            Path destinationFile = rootLocation.resolve(relativePath).normalize();
            if (!destinationFile.startsWith(rootLocation)) {
                throw new IllegalArgumentException("非法文件路径");
            }

            Files.createDirectories(destinationFile.getParent());
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            log.info("文件上传成功: path={}, size={}", relativePath, file.getSize());
            return relativePath;

        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void delete(String fileKey) {
        ensureInitialized();
        validateFileKey(fileKey);
        Path file = rootLocation.resolve(fileKey).normalize();
        if (!file.startsWith(rootLocation)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        try {
            Files.deleteIfExists(file);
            log.info("文件删除成功: path={}", fileKey);
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }

    @Override
    public String getUrl(String fileKey) {
        return properties.getLocal().getBaseUrl() + "/" + fileKey;
    }

    @Override
    public InputStream getInputStream(String fileKey) {
        ensureInitialized();
        validateFileKey(fileKey);
        try {
            Path file = rootLocation.resolve(fileKey).normalize();
            if (!file.startsWith(rootLocation)) {
                throw new IllegalArgumentException("非法文件路径");
            }
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get input stream for file: " + fileKey, e);
        }
    }

    @Override
    public boolean exists(String fileKey) {
        ensureInitialized();
        validateFileKey(fileKey);
        Path file = rootLocation.resolve(fileKey).normalize();
        if (!file.startsWith(rootLocation)) {
            return false;
        }
        return Files.exists(file);
    }

    @Override
    public long getFileSize(String fileKey) {
        ensureInitialized();
        validateFileKey(fileKey);
        try {
            Path file = rootLocation.resolve(fileKey).normalize();
            if (!file.startsWith(rootLocation)) {
                return -1;
            }
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private void ensureInitialized() {
        if (rootLocation == null) {
            init();
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category 不能为空");
        }
        if (category.startsWith("/") || category.contains("\\") || category.contains("..")) {
            throw new IllegalArgumentException("非法 category");
        }
    }

    private void validateCustomFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("非法 fileName");
        }
    }

    private void validateFileKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey 不能为空");
        }
        if (fileKey.startsWith("/") || fileKey.contains("\\") || fileKey.contains("..")) {
            throw new IllegalArgumentException("非法 fileKey");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    private String generateFileName(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return extension.isEmpty() ? uuid : uuid + "." + extension;
    }
}
