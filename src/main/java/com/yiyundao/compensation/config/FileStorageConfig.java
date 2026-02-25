package com.yiyundao.compensation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置
 * <p>
 * 文件存储实现类通过 @Service + @ConditionalOnProperty 注解自动选择，
 * 无需手动配置 Bean。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Configuration
public class FileStorageConfig {
    // 文件存储实现通过 FileStorageProperties 配置自动选择：
    // - LocalFileService (file-storage.active=local 或未配置)
    // - MinioFileService (file-storage.active=minio)
}
