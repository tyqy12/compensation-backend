package com.yiyundao.compensation.service.impl;

import com.yiyundao.compensation.config.FileStorageProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

final class FileUploadValidator {

    private FileUploadValidator() {
    }

    static void validate(MultipartFile file, FileStorageProperties properties) {
        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (properties == null) {
            throw new IllegalArgumentException("文件存储配置不能为空");
        }

        String filename = StringUtils.getFilename(file.getOriginalFilename());
        String extension = getFileExtension(filename).toLowerCase(Locale.ROOT);

        if (!isAllowedExtension(extension, properties)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }

        if (file.getSize() > properties.getMaxFileSize()) {
            throw new IllegalArgumentException("文件大小超出限制");
        }
    }

    static void validateStoredFileName(String fileName, FileStorageProperties properties) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        if (properties == null) {
            throw new IllegalArgumentException("文件存储配置不能为空");
        }
        String extension = getFileExtension(fileName).toLowerCase(Locale.ROOT);
        if (!isAllowedExtension(extension, properties)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }
    }

    private static String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    private static boolean isAllowedExtension(String extension, FileStorageProperties properties) {
        return properties.getAllowedExtensions().stream()
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(extension::equals);
    }
}
