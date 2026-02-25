package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String category) {

        String fileKey = fileService.upload(file, category);
        String url = fileService.getUrl(fileKey);

        return ApiResponse.success(url);
    }

    @Operation(summary = "批量上传文件")
    @PostMapping("/upload/batch")
    public ApiResponse<List<String>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "general") String category) {

        List<String> urls = files.stream()
                .map(file -> {
                    String fileKey = fileService.upload(file, category);
                    return fileService.getUrl(fileKey);
                })
                .toList();

        return ApiResponse.success(urls);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam String fileKey) {
        fileService.delete(fileKey);
        return ApiResponse.success();
    }

    @Operation(summary = "获取文件 URL")
    @GetMapping("/url")
    public ApiResponse<String> getUrl(@RequestParam String fileKey) {
        return ApiResponse.success(fileService.getUrl(fileKey));
    }
}
