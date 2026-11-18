package com.factori.platform.service;

import com.factori.platform.config.FactoriProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ArtifactStorage {
    private static final long MAX_ICON_BYTES = 5 * 1024 * 1024;
    private static final long MAX_ICON_PIXELS = 20_000_000;
    private static final long MAX_APK_BYTES = 300 * 1024 * 1024;
    private static final long MAX_LOG_BYTES = 10 * 1024 * 1024;
    private final Path root;

    public ArtifactStorage(FactoriProperties properties) {
        this.root = properties.artifactRoot().toAbsolutePath().normalize();
    }

    public String storeIcon(UUID appId, byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_ICON_BYTES) {
            throw new InvalidIconException("Icon file must be between 1 byte and 5 MiB.");
        }
        BufferedImage image = decodeImage(bytes);
        Path destination = safePath("icons/" + appId + ".png");
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), appId.toString(), ".tmp");
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("PNG image encoder is unavailable.");
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return "icons/" + appId + ".png";
    }

    public Path iconPath(String storageKey) throws IOException {
        Path path = safePath(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Store icon is unavailable.");
        }
        return path;
    }

    public StoredArtifact storeBuildOutput(UUID appId, UUID buildId, Path apkPath, Path logPath)
            throws IOException {
        if (!Files.isRegularFile(apkPath, LinkOption.NOFOLLOW_LINKS)
                || Files.size(apkPath) == 0
                || Files.size(apkPath) > MAX_APK_BYTES) {
            throw new IOException("Build worker returned an invalid APK artifact.");
        }
        String base = "apps/" + appId + "/builds/" + buildId;
        Path apkDestination = safePath(base + "/app.apk");
        Path logDestination = safePath(base + "/build.log");
        Files.createDirectories(apkDestination.getParent());
        Files.copy(apkPath, apkDestination, StandardCopyOption.REPLACE_EXISTING);
        copyBoundedLog(logPath, logDestination);
        return new StoredArtifact(
                base + "/app.apk",
                sha256(apkDestination),
                base + "/build.log");
    }

    public String storeBuildLog(UUID appId, UUID buildId, Path logPath) throws IOException {
        String key = "apps/" + appId + "/builds/" + buildId + "/build.log";
        Path destination = safePath(key);
        Files.createDirectories(destination.getParent());
        copyBoundedLog(logPath, destination);
        return key;
    }

    public Path artifactPath(String storageKey) throws IOException {
        Path path = safePath(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IOException("The requested artifact is unavailable.");
        }
        return path;
    }

    private BufferedImage decodeImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new InvalidIconException("Icon file is not a supported image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidIconException("Icon file must be a PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) {
                    throw new InvalidIconException("Icon file must be a PNG or JPEG image.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_ICON_PIXELS) {
                    throw new InvalidIconException("Icon dimensions must not exceed 20 megapixels.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidIconException("Icon image could not be decoded.");
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        }
    }

    private Path safePath(String storageKey) {
        Path result = root.resolve(storageKey).normalize();
        if (!result.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        return result;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by this runtime.", exception);
        }
    }

    private void copyBoundedLog(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.writeString(destination, "Build worker did not produce a log file.");
            return;
        }
        long length = Files.size(source);
        try (var input = Files.newInputStream(source)) {
            if (length > MAX_LOG_BYTES) {
                input.skipNBytes(length - MAX_LOG_BYTES);
            }
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record StoredArtifact(String artifactKey, String sha256, String logKey) {
    }

    public static class InvalidIconException extends RuntimeException {
        public InvalidIconException(String message) {
            super(message);
        }
    }
}
