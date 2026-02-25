# Dynamic Configuration Management

## Overview

The Compensation Assistant System supports dynamic configuration management for all third-party service integrations. This allows administrators to configure and update service credentials and settings through the admin UI without modifying application configuration files or restarting the service.

## Architecture

### Configuration Storage
- **Database Table**: `integration_config`
- **Encryption**: All sensitive configuration data is encrypted using SM4/AES encryption before storage
- **Service**: `IntegrationConfigService` provides unified access to all platform configurations

### Supported Platforms

1. **WeChat (企业微信)** - Platform type: `wechat`
2. **DingTalk (钉钉)** - Platform type: `dingtalk`
3. **Feishu (飞书)** - Platform type: `feishu`
4. **Alipay (支付宝)** - Platform type: `alipay`
5. **SMS (短信服务)** - Platform type: `sms`
6. **Email (邮件服务)** - Platform type: `email`
7. **Encryption (加密配置)** - Platform type: `encryption`

## Configuration Types and Required Fields

### WeChat Configuration (`wechat`)
```json
{
  "corpid": "企业ID",
  "corpsecret": "应用密钥",
  "agentid": "应用ID"
}
```

### DingTalk Configuration (`dingtalk`)
```json
{
  "appKey": "应用AppKey",
  "appSecret": "应用AppSecret",
  "agentId": "应用AgentId"
}
```

### Feishu Configuration (`feishu`)
```json
{
  "appId": "应用ID",
  "appSecret": "应用密钥"
}
```

### Alipay Configuration (`alipay`)
```json
{
  "appId": "应用ID",
  "privateKey": "应用私钥",
  "publicKey": "支付宝平台公钥",
  "serverUrl": "支付宝网关地址",
  "charset": "UTF-8",
  "signType": "RSA2",
  "format": "json"
}
```

### SMS Configuration (`sms`)
```json
{
  "provider": "aliyun|tencent|huawei|mock",
  "accessKeyId": "阿里云AccessKeyId",
  "accessKeySecret": "阿里云AccessKeySecret",
  "signName": "短信签名",
  "templateCode": "默认模板代码",
  "secretId": "腾讯云SecretId",
  "secretKey": "腾讯云SecretKey",
  "appId": "腾讯云AppId",
  "sdkAppId": "腾讯云SdkAppId",
  "appKey": "华为云AppKey",
  "appSecret": "华为云AppSecret",
  "sender": "华为云发送方",
  "templateId": "华为云模板ID",
  "endpoint": "服务端点",
  "region": "地域",
  "dailyLimit": 10000,
  "rateLimitPerMinute": 60,
  "enabled": true
}
```

### Email Configuration (`email`)
```json
{
  "host": "SMTP服务器地址",
  "port": 587,
  "username": "SMTP用户名",
  "password": "SMTP密码",
  "fromAddress": "发件人邮箱",
  "fromName": "发件人名称",
  "ssl": false,
  "tls": true,
  "encoding": "UTF-8",
  "enabled": true
}
```

### Encryption Configuration (`encryption`)
```json
{
  "aesKey": "AES加密密钥(至少16字符)",
  "sm4Key": "SM4加密密钥(至少16字符)",
  "algorithm": "SM4+AES",
  "keyDerivation": "SHA-256",
  "keyRotationDays": 90,
  "enabled": true
}
```

## Service Integration

### Configuration Retrieval
Services use `IntegrationConfigService` to retrieve configuration:

```java
// Check if platform is enabled
boolean enabled = integrationConfigService.isPlatformEnabled("wechat");

// Get typed configuration
WechatConfigDto wechatConfig = integrationConfigService.getWechatConfig();

// Get generic configuration map
Map<String, String> config = integrationConfigService.getDecryptedConfig("wechat");

// Get specific configuration value
String corpid = integrationConfigService.getConfigValue("wechat", "corpid");
```

### Fallback Configuration
- **EncryptionService**: Falls back to `application.yml` values when dynamic config is unavailable
- **Other Services**: Fail gracefully when configuration is missing or invalid
- **Development Mode**: Mock providers available for testing without real API credentials

### Configuration Caching
- **EncryptionService**: Caches keys for 5 minutes to avoid frequent database queries
- **Manual Refresh**: `forceRefreshKeys()` method available for immediate updates
- **Connection Validation**: All adapters implement `checkConnection()` for configuration validation

## Admin Management

### Configuration CRUD Operations
```java
// Save or update configuration
integrationConfigService.saveOrUpdate("wechat", configJson, true);

// Get raw configuration (encrypted)
IntegrationConfig rawConfig = integrationConfigService.getRawConfig("wechat");

// Check if platform is enabled
boolean enabled = integrationConfigService.isPlatformEnabled("wechat");
```

### Security Considerations
1. **Encryption**: All sensitive data encrypted before database storage
2. **Access Control**: Admin role required for configuration management
3. **Audit Logging**: Configuration changes should be logged
4. **Key Rotation**: Encryption keys should be rotated periodically

## Testing and Validation

### Connection Testing
Each service adapter provides connection validation:

```java
// Test WeChat connection
boolean wechatOk = weChatNotificationAdapter.checkConnection();

// Test Alipay configuration
boolean alipayOk = alipayService.checkAlipayConnection();

// Test encryption configuration
boolean encryptionOk = encryptionService.checkEncryptionConfig();
```

### Development Configuration
For development and testing, use the following configuration values:

#### SMS Configuration (Mock Provider)
```json
{
  "provider": "mock",
  "enabled": true
}
```

#### Encryption Configuration (Development)
```json
{
  "aesKey": "dev_aes_key_32_characters_long",
  "sm4Key": "dev_sm4_key_32_characters_long",
  "algorithm": "SM4+AES",
  "enabled": true
}
```

## Migration from Application.yml

### Before (Static Configuration)
```yaml
# application.yml
wechat:
  corpid: "your-corp-id"
  corpsecret: "your-corp-secret"

encryption:
  sm4:
    key: "your-sm4-key"
  aes:
    key: "your-aes-key"
```

### After (Dynamic Configuration)
1. Remove configuration from `application.yml`
2. Insert configuration via admin UI or database
3. Services automatically load from dynamic configuration
4. Fallback to static configuration if dynamic config unavailable

## Error Handling

### Configuration Not Found
- **NotificationAdapters**: Return `false` from `checkConnection()`
- **AlipayService**: Throw `IllegalStateException` with descriptive message
- **EncryptionService**: Fall back to static configuration with warning log

### Invalid Configuration
- **Validation**: Each service validates required fields before use
- **Graceful Degradation**: Services fail gracefully when configuration is invalid
- **Logging**: Clear error messages for troubleshooting

## Monitoring and Alerts

### Health Checks
Monitor configuration health through:
1. Connection test endpoints for each platform
2. Encryption service validation
3. Configuration completeness checks

### Recommended Monitoring
- Configuration last modified timestamp
- Connection test success rates
- Encryption key rotation status
- Failed configuration access attempts

## Best Practices

1. **Environment Separation**: Use different configurations for dev/staging/production
2. **Key Rotation**: Regularly rotate encryption keys
3. **Access Control**: Restrict configuration access to authorized admins
4. **Backup**: Regular backup of configuration data
5. **Testing**: Test configuration changes in staging before production
6. **Documentation**: Keep configuration documentation up to date

## API Endpoints

### Configuration Management
- `GET /admin/integration-configs` - List all configurations
- `POST /admin/integration-configs` - Create/update configuration
- `GET /admin/integration-configs/{platformType}` - Get specific configuration
- `DELETE /admin/integration-configs/{platformType}` - Disable configuration
- `POST /admin/integration-configs/{platformType}/test` - Test configuration connection

### Connection Testing
- `GET /admin/system/health/integrations` - Test all integration connections
- `POST /admin/system/encryption/test` - Test encryption configuration
- `POST /admin/system/encryption/refresh-keys` - Force refresh encryption keys