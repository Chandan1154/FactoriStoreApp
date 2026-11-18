package com.factori.platform.service;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StoreConfigValidator {
    private static final Pattern PACKAGE_PART = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern NUMERIC_HOST = Pattern.compile("[0-9.]+");
    private static final List<String> RESERVED_SUFFIXES =
            List.of(".local", ".localhost", ".internal", ".test", ".example", ".invalid");

    public NormalizedStore normalize(String name, String rawUrl) {
        return normalize(name, rawUrl, null);
    }

    public NormalizedStore normalize(String name, String rawUrl, UUID tenantId) {
        String normalizedName = name == null ? "" : name.strip();
        if (normalizedName.isEmpty() || normalizedName.length() > 64
                || normalizedName.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidStoreConfigurationException("Store name must be 1–64 printable characters.");
        }
        String value = rawUrl == null ? "" : rawUrl.strip();
        if (value.isEmpty()) {
            throw new InvalidStoreConfigurationException("Store URL is required.");
        }
        if (!value.contains("://")) {
            value = "https://" + value;
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new InvalidStoreConfigurationException("Store URL is not a valid URL.");
        }
        String scheme = uri.getScheme();
        String rawHost = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || rawHost == null
                || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new InvalidStoreConfigurationException("Store URL must be a public HTTPS URL without credentials or a nonstandard port.");
        }

        String host;
        try {
            String hostName = rawHost.endsWith(".")
                    ? rawHost.substring(0, rawHost.length() - 1)
                    : rawHost;
            host = IDN.toASCII(hostName).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new InvalidStoreConfigurationException("Store URL contains an invalid hostname.");
        }
        if (!host.contains(".") || NUMERIC_HOST.matcher(host).matches()
                || RESERVED_SUFFIXES.stream().anyMatch(host::endsWith)
                || !validDnsName(host)) {
            throw new InvalidStoreConfigurationException("Store URL must use a public DNS hostname.");
        }

        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String normalizedUrl = "https://" + host + path + query;
        return new NormalizedStore(
                normalizedName,
                normalizedUrl,
                host,
                applicationId(host, normalizedName, tenantId));
    }

    private boolean validDnsName(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || !label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
                return false;
            }
        }
        return true;
    }

    private String applicationId(String host, String appName, UUID tenantId) {
        String[] domainLabels = host.split("\\.");
        List<String> parts = new ArrayList<>();
        for (int index = domainLabels.length - 1; index >= 0 && parts.size() < 3; index--) {
            String label = domainLabels[index].replaceAll("[^a-z0-9]", "");
            if (!label.isEmpty()) {
                parts.add(label.matches("^[a-z].*")
                        ? label.substring(0, Math.min(63, label.length()))
                        : "h" + label.substring(0, Math.min(62, label.length())));
            }
        }
        String input = host + "|" + appName.toLowerCase(Locale.ROOT)
                + (tenantId == null ? "" : "|" + tenantId);
        String digest;
        try {
            digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 10);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by this runtime.", exception);
        }
        String appId = "com.factori.store." + String.join(".", parts) + ".a" + digest;
        if (appId.length() > 200 || !List.of(appId.split("\\.")).stream()
                .allMatch(part -> PACKAGE_PART.matcher(part).matches())) {
            throw new InvalidStoreConfigurationException("Could not derive a valid Android application ID.");
        }
        return appId;
    }

    public record NormalizedStore(String name, String url, String host, String applicationId) {
    }

    public static class InvalidStoreConfigurationException extends RuntimeException {
        public InvalidStoreConfigurationException(String message) {
            super(message);
        }
    }
}
