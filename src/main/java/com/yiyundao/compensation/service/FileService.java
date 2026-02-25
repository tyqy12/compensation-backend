package com.yiyundao.compensation.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileService {

    String upload(MultipartFile file, String category);

    String upload(MultipartFile file, String category, String fileName);

    void delete(String fileKey);

    String getUrl(String fileKey);

    InputStream getInputStream(String fileKey);

    boolean exists(String fileKey);

    long getFileSize(String fileKey);
}
