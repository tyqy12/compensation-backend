package com.yiyundao.compensation.modules.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;

import java.util.List;

public interface AppRegistryService extends IService<AppRegistry> {

    RegisteredClient register(AppRegistryCreateCommand cmd);

    RegisteredClient rotateSecret(Long appId);

    AppRegistry updateApp(Long appId, AppRegistryUpdateCommand cmd);

    AppRegistry findEnabledByClientId(String clientId);

    boolean matchesSecret(AppRegistry app, String rawSecret);

    boolean isIpAllowed(AppRegistry app, String clientIp);

    List<String> resolveScopes(AppRegistry app);

    List<String> resolveIpWhitelist(AppRegistry app);

    record AppRegistryCreateCommand(String appName,
                                    List<String> scopes,
                                    List<String> ipWhitelist,
                                    String webhookUrl,
                                    String status) {}

    record AppRegistryUpdateCommand(String appName,
                                    List<String> scopes,
                                    List<String> ipWhitelist,
                                    String webhookUrl,
                                    String status) {}

    record RegisteredClient(AppRegistry appRegistry, String clientSecretPlain) {}
}
