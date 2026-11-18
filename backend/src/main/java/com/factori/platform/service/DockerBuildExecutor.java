package com.factori.platform.service;

import com.factori.platform.config.FactoriProperties;
import com.factori.platform.service.ArtifactStorage.StoredArtifact;
import com.factori.platform.service.PlatformExceptions.BuildExecutionException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class DockerBuildExecutor {
    private static final System.Logger LOGGER =
            System.getLogger(DockerBuildExecutor.class.getName());
    private static final Set<String> TEMPLATE_FILES = Set.of(
            "app",
            "gradle",
            "scripts/generate-apk.sh",
            "scripts/generate-apk.py",
            "scripts/generate-store-icon.py",
            "scripts/validate-apk.sh",
            "gradlew",
            "gradle.properties",
            "build.gradle.kts",
            "settings.gradle.kts");

    private final FactoriProperties properties;
    private final ArtifactStorage storage;

    public DockerBuildExecutor(FactoriProperties properties, ArtifactStorage storage) {
        this.properties = properties;
        this.storage = storage;
    }

    public BuildExecutionResult execute(BuildContext build) {
        if (properties.buildImage() == null || properties.buildImage().isBlank()) {
            throw new BuildExecutionException(
                    "BUILD_WORKER_UNAVAILABLE",
                    null,
                    new IllegalStateException("FACTORY_BUILD_IMAGE is not configured."));
        }
        Path workspace;
        try {
            Files.createDirectories(properties.workspaceRoot());
            workspace = Files.createTempDirectory(
                    properties.workspaceRoot().toAbsolutePath().normalize(),
                    "build-" + build.buildId() + "-");
        } catch (IOException exception) {
            throw new BuildExecutionException("WORKSPACE_CREATE_FAILED", null, exception);
        }

        Path log = workspace.resolve("build.log");
        String logKey = null;
        Process runningProcess = null;
        try {
            copyTemplate(workspace);
            Path icon = workspace.resolve("input/store-icon.png");
            Files.createDirectories(icon.getParent());
            Files.copy(storage.iconPath(build.iconStorageKey()), icon);
            Files.createDirectories(workspace.resolve("output"));

            List<String> command = List.of(
                    "docker",
                    "run",
                    "--rm",
                    "--network=none",
                    "--cpus=2.0",
                    "--memory=3g",
                    "--pids-limit=256",
                    "--cap-drop=ALL",
                    "--security-opt=no-new-privileges",
                    "--tmpfs",
                    "/tmp:rw,nosuid,nodev,size=512m",
                    "--user=1000:1000",
                    "--volume",
                    workspace + ":/workspace:rw",
                    "--workdir",
                    "/workspace",
                    "--env",
                    "GRADLE_BIN=gradle",
                    properties.buildImage(),
                    "bash",
                    "scripts/generate-apk.sh",
                    "--store-name",
                    build.storeName(),
                    "--store-url",
                    build.storeUrl(),
                    "--application-id",
                    build.applicationId(),
                    "--version-code",
                    Integer.toString(build.versionCode()),
                    "--version-name",
                    build.versionName(),
                    "--icon",
                    "/workspace/input/store-icon.png",
                    "--output",
                    "/workspace/output/app.apk",
                    "--offline");

            runningProcess = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            boolean finished = runningProcess.waitFor(
                    Duration.ofSeconds(properties.buildTimeoutSeconds()).toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!finished) {
                runningProcess.destroyForcibly();
                runningProcess.waitFor();
                logKey = persistLog(build, log, null);
                throw new BuildExecutionException(
                        "BUILD_TIMEOUT",
                        logKey,
                        new IllegalStateException("The isolated worker exceeded its build timeout."));
            }
            if (runningProcess.exitValue() != 0) {
                logKey = persistLog(build, log, null);
                throw new BuildExecutionException(
                        "BUILD_FAILED",
                        logKey,
                        new IllegalStateException("The isolated build worker returned a non-zero exit code."));
            }

            Path apk = workspace.resolve("output/app.apk");
            StoredArtifact stored = storage.storeBuildOutput(
                    build.appId(), build.buildId(), apk, log);
            return new BuildExecutionResult(
                    stored.artifactKey(),
                    stored.sha256(),
                    stored.logKey());
        } catch (BuildExecutionException exception) {
            throw exception;
        } catch (IOException exception) {
            logKey = persistLog(build, log, exception);
            throw new BuildExecutionException("BUILD_WORKER_ERROR", logKey, exception);
        } catch (InterruptedException exception) {
            if (runningProcess != null) {
                runningProcess.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            logKey = persistLog(build, log, exception);
            throw new BuildExecutionException("BUILD_INTERRUPTED", logKey, exception);
        } finally {
            try {
                deleteTree(workspace);
            } catch (IOException exception) {
                LOGGER.log(
                        System.Logger.Level.ERROR,
                        "Could not remove isolated build workspace {0}: {1}",
                        workspace,
                        exception.getMessage());
            }
        }
    }

    private void copyTemplate(Path workspace) throws IOException {
        Path template = properties.templateRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(template)) {
            throw new IOException("Configured Android template directory is unavailable.");
        }
        for (String relativePath : TEMPLATE_FILES) {
            Path source = template.resolve(relativePath);
            if (!Files.exists(source)) {
                throw new IOException("Required Android template content is missing.");
            }
            Path destination = workspace.resolve(relativePath);
            if (Files.isDirectory(source)) {
                copyTree(source, destination);
            } else {
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination);
            }
        }
    }

    private void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (!directory.equals(source) && Set.of(".gradle", "build", ".idea")
                        .contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String persistLog(BuildContext build, Path log, Exception originalFailure) {
        try {
            return storage.storeBuildLog(build.appId(), build.buildId(), log);
        } catch (IOException logFailure) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(logFailure);
            }
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Could not persist build log for build {0}: {1}",
                    build.buildId(),
                    logFailure.getMessage());
            return null;
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                    throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
