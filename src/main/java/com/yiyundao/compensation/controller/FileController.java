package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/v1/files")
@SecurityAnnotations.IsFinanceOrHrOrAdmin
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String category) {

        String fileKey = fileService.upload(file, category);
        try {
            String url = fileService.getUrl(fileKey);

            return ApiResponse.success(url);
        } catch (RuntimeException e) {
            cleanupUploadedFiles(List.of(fileKey));
            throw e;
        }
    }

    @Operation(summary = "批量上传文件")
    @PostMapping("/upload/batch")
    public ApiResponse<List<String>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "general") String category) {

        List<String> uploadedFileKeys = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String fileKey = fileService.upload(file, category);
                uploadedFileKeys.add(fileKey);
                urls.add(fileService.getUrl(fileKey));
            }
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileKeys);
            throw e;
        }

        return ApiResponse.success(urls);
    }

    private void cleanupUploadedFiles(List<String> fileKeys) {
        for (String fileKey : fileKeys) {
            try {
                fileService.delete(fileKey);
            } catch (RuntimeException cleanupEx) {
                log.warn("上传失败补偿删除文件失败: fileKey={}, msg={}", fileKey, cleanupEx.getMessage());
            }
        }
    }

    @Operation(summary = "删除文件")
    @DeleteMapping
    @SecurityAnnotations.IsAdmin
    public ApiResponse<Void> delete(@RequestParam String fileKey) {
        fileService.delete(fileKey);
        return ApiResponse.success();
    }

    @Operation(summary = "获取文件 URL")
    @GetMapping("/url")
    public ApiResponse<String> getUrl(@RequestParam String fileKey) {
        return ApiResponse.success(fileService.getUrl(fileKey));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String fileKey) {
        if (!fileService.exists(fileKey)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        try (InputStream inputStream = fileService.getInputStream(fileKey)) {
            byte[] content = inputStream.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(content.length);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(resolveDownloadFileName(fileKey))
                    .build());
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(content);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败");
        }
    }

    private String resolveDownloadFileName(String fileKey) {
        int lastSlashIndex = fileKey.lastIndexOf('/');
        return lastSlashIndex >= 0 ? fileKey.substring(lastSlashIndex + 1) : fileKey;
    }
}
