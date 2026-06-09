package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileControllerTest {

    private final FileService fileService = mock(FileService.class);
    private final FileController controller = new FileController(fileService);

    @Test
    void uploadShouldDeleteStoredFileWhenUrlGenerationFails() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "doc.txt",
                "text/plain",
                "content".getBytes()
        );
        when(fileService.upload(file, "docs")).thenReturn("docs/2026/06/06/doc.txt");
        when(fileService.getUrl("docs/2026/06/06/doc.txt"))
                .thenThrow(new IllegalStateException("url failed"));

        assertThatThrownBy(() -> controller.upload(file, "docs"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("url failed");

        verify(fileService).delete("docs/2026/06/06/doc.txt");
    }

    @Test
    void uploadBatchShouldDeleteAlreadyUploadedFilesWhenLaterUploadFails() {
        MultipartFile first = new MockMultipartFile(
                "files",
                "first.txt",
                "text/plain",
                "first".getBytes()
        );
        MultipartFile second = new MockMultipartFile(
                "files",
                "second.txt",
                "text/plain",
                "second".getBytes()
        );
        when(fileService.upload(first, "docs")).thenReturn("docs/2026/06/06/first.txt");
        when(fileService.getUrl("docs/2026/06/06/first.txt")).thenReturn("http://files/first.txt");
        when(fileService.upload(second, "docs")).thenThrow(new IllegalArgumentException("bad file"));

        assertThatThrownBy(() -> controller.uploadBatch(List.of(first, second), "docs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad file");

        verify(fileService).delete("docs/2026/06/06/first.txt");
    }

    @Test
    void uploadBatchShouldNotDeleteFilesWhenAllUploadsSucceed() {
        MultipartFile first = new MockMultipartFile(
                "files",
                "first.txt",
                "text/plain",
                "first".getBytes()
        );
        MultipartFile second = new MockMultipartFile(
                "files",
                "second.txt",
                "text/plain",
                "second".getBytes()
        );
        when(fileService.upload(first, "docs")).thenReturn("docs/2026/06/06/first.txt");
        when(fileService.getUrl("docs/2026/06/06/first.txt")).thenReturn("http://files/first.txt");
        when(fileService.upload(second, "docs")).thenReturn("docs/2026/06/06/second.txt");
        when(fileService.getUrl("docs/2026/06/06/second.txt")).thenReturn("http://files/second.txt");

        ApiResponse<List<String>> response = controller.uploadBatch(List.of(first, second), "docs");

        assertThat(response.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getData()).containsExactly("http://files/first.txt", "http://files/second.txt");
        verify(fileService, never()).delete("docs/2026/06/06/first.txt");
        verify(fileService, never()).delete("docs/2026/06/06/second.txt");
    }

    @Test
    void downloadShouldReturnFileContentWithSafeDisposition() {
        when(fileService.exists("docs/2026/06/06/report.txt")).thenReturn(true);
        when(fileService.getInputStream("docs/2026/06/06/report.txt"))
                .thenReturn(new ByteArrayInputStream("report".getBytes()));

        ResponseEntity<byte[]> response = controller.download("docs/2026/06/06/report.txt");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("report".getBytes());
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("report.txt");
    }

    @Test
    void downloadShouldRejectMissingFile() {
        when(fileService.exists("docs/2026/06/06/missing.txt")).thenReturn(false);

        assertThatThrownBy(() -> controller.download("docs/2026/06/06/missing.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件不存在");

        verify(fileService, never()).getInputStream("docs/2026/06/06/missing.txt");
    }
}
