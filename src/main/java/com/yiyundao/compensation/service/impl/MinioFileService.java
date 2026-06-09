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
import java.util.Map;
import java.util.Locale;
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
            minioClient = createMinioClient(minio);

            ensureBucketExists(minio.getBucket());

        } catch (Exception e) {
            log.warn("MinIO 客户端初始化失败，将使用本地存储: {}", e.getMessage());
            LocalFileService localFileService = new LocalFileService(properties);
            localFileService.init();
            fallbackService = localFileService;
        }
    }

    MinioClient createMinioClient(FileStorageProperties.MinioStorage minio) {
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("创建 MinIO Bucket: {}", bucketName);
        }
    }

    @Override
    public String upload(MultipartFile file, String category) {
        return upload(file, category, null);
    }

    @Override
    public String upload(MultipartFile file, String category, String fileName) {
        FilePathValidator.validateCategory(category);
        FilePathValidator.validateCustomFileName(fileName);
        FileUploadValidator.validate(file, properties);
        FileUploadValidator.validateStoredFileName(fileName, properties);
        if (fallbackService != null) {
            return fallbackService.upload(file, category, fileName);
        }
        try {
            String extension = getFileExtension(file.getOriginalFilename());
            if (fileName == null || fileName.isEmpty()) {
                fileName = generateFileName(extension);
            }
            FileUploadValidator.validateStoredFileName(fileName, properties);

            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectName = category + "/" + datePath + "/" + fileName;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getMinio().getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(resolveStoredContentType(fileName))
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
        FilePathValidator.validateFileKey(fileKey);
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
        FilePathValidator.validateFileKey(fileKey);
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
                            .extraQueryParams(downloadResponseParams(fileKey))
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件 URL 失败", e);
        }
    }

    @Override
    public InputStream getInputStream(String fileKey) {
        FilePathValidator.validateFileKey(fileKey);
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
        FilePathValidator.validateFileKey(fileKey);
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
        FilePathValidator.validateFileKey(fileKey);
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

    private String resolveStoredContentType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private Map<String, String> downloadResponseParams(String fileKey) {
        return Map.of(
                "response-content-type", "application/octet-stream",
                "response-content-disposition", "attachment; filename=\"" + extractDownloadFileName(fileKey) + "\""
        );
    }

    private String extractDownloadFileName(String fileKey) {
        int lastSlashIndex = fileKey.lastIndexOf('/');
        return lastSlashIndex >= 0 ? fileKey.substring(lastSlashIndex + 1) : fileKey;
    }
}
