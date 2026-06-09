package com.yiyundao.compensation.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String TRUSTED_PROXY_PROPERTY = "security.trusted-proxies";
    private static final String TRUSTED_PROXY_ENV = "SECURITY_TRUSTED_PROXIES";

    private final Environment environment;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = firstValidForwardedIp(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(forwarded)) {
            return forwarded;
        }
        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        return StringUtils.hasText(realIp) ? realIp : remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        String allow = environment.getProperty(TRUSTED_PROXY_PROPERTY);
        if (!StringUtils.hasText(allow)) {
            allow = environment.getProperty(TRUSTED_PROXY_ENV);
        }
        if (!StringUtils.hasText(allow)) {
            return false;
        }
        return Arrays.stream(allow.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(rule -> matchesRule(remoteAddr, rule));
    }

    private boolean matchesRule(String remoteAddr, String rule) {
        if (rule.contains("/")) {
            return matchesCidr(remoteAddr, rule);
        }
        return remoteAddr.equals(rule);
    }

    private String firstValidForwardedIp(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        for (String part : header.split(",")) {
            String ip = normalizeIp(part);
            if (StringUtils.hasText(ip)) {
                return ip;
            }
        }
        return null;
    }

    private String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.trim();
        if (!StringUtils.hasText(candidate) || "unknown".equalsIgnoreCase(candidate)) {
            return null;
        }
        if (candidate.startsWith("[") && candidate.contains("]")) {
            return candidate.substring(1, candidate.indexOf(']'));
        }
        if (candidate.chars().filter(ch -> ch == ':').count() == 1 && candidate.contains(".")) {
            int portIndex = candidate.lastIndexOf(':');
            if (portIndex > 0 && candidate.substring(portIndex + 1).chars().allMatch(Character::isDigit)) {
                return candidate.substring(0, portIndex);
            }
        }
        return candidate;
    }

    private boolean matchesCidr(String remoteAddr, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                return false;
            }
            byte[] address = InetAddress.getByName(remoteAddr).getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            if (address.length != network.length) {
                return false;
            }
            int prefixLength = Integer.parseInt(parts[1]);
            int maxBits = address.length * 8;
            if (prefixLength < 0 || prefixLength > maxBits) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        } catch (NumberFormatException | UnknownHostException e) {
            return false;
        }
    }
}
