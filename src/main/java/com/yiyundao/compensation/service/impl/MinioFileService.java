package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.service.FileService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file-storage.active", havingValue = "minio")
public class MinioFileService implements FileService {

    private final FileStorageProperties properties;
    private MinioClient minioClient;
    private FileService fallbackService;

    @PostConstruct
    public void init() {
        try {
            FileStorageProperties.MinioStorage minio = properties.getMinio();
            minioClient = MinioClient.builder()
                    .endpoint(minio.getEndpoint())
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .build();

            ensureBucketExists(minio.getBucket());

        } catch (Exception e) {
            log.warn("MinIO 客户端初始化失败，将使用本地存储: {}", e.getMessage());
            LocalFileService localFileService = new LocalFileService(properties);
            localFileService.init();
            fallbackService = localFileService;
        }
    }

    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建 MinIO Bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("检查 Bucket 失败: {}", e.getMessage());
        }
    }

    @Override
    public String upload(MultipartFile file, String category) {
        return upload(file, category, null);
    }

    @Override
    public String upload(MultipartFile file, String category, String fileName) {
        validateCategory(category);
        validateCustomFileName(fileName);
        if (fallbackService != null) {
            return fallbackService.upload(file, category, fileName);
        }
        FileUploadValidator.validate(file, properties);
        try {
            String extension = getFileExtension(file.getOriginalFilename());
            if (fileName == null || fileName.isEmpty()) {
                fileName = generateFileName(extension);
            }

            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectName = category + "/" + datePath + "/" + fileName;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("文件上传成功: object={}, size={}", objectName, file.getSize());
            return objectName;

        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件上传失败", e);
        }
    }

    @Override
    public void delete(String fileKey) {
        if (fallbackService != null) {
            fallbackService.delete(fileKey);
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
            log.info("文件删除成功: object={}", fileKey);
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件删除失败", e);
        }
    }

    @Override
    public String getUrl(String fileKey) {
        if (fallbackService != null) {
            return fallbackService.getUrl(fileKey);
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .method(Method.GET)
                            .expiry(24 * 60 * 60)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件 URL 失败", e);
        }
    }

    @Override
    public InputStream getInputStream(String fileKey) {
        if (fallbackService != null) {
            return fallbackService.getInputStream(fileKey);
        }
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件流失败", e);
        }
    }

    @Override
    public boolean exists(String fileKey) {
        if (fallbackService != null) {
            return fallbackService.exists(fileKey);
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getFileSize(String fileKey) {
        if (fallbackService != null) {
            return fallbackService.getFileSize(fileKey);
        }
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(fileKey)
                            .build()
            );
            return stat.size();
        } catch (Exception e) {
            return -1;
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
}
