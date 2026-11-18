package com.factori.platform.service;

import com.factori.platform.service.PlatformExceptions.BuildExecutionException;
import com.factori.platform.service.PlatformExceptions.BuildQueueFullException;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Component
public class BuildJobRunner {
    private static final System.Logger LOGGER = System.getLogger(BuildJobRunner.class.getName());
    private final Executor executor;
    private final BuildStateService stateService;
    private final DockerBuildExecutor buildExecutor;

    public BuildJobRunner(
            @Qualifier("buildWorkerExecutor") Executor executor,
            BuildStateService stateService,
            DockerBuildExecutor buildExecutor) {
        this.executor = executor;
        this.stateService = stateService;
        this.buildExecutor = buildExecutor;
    }

    public void submit(UUID tenantId, UUID buildId) {
        try {
            executor.execute(() -> run(tenantId, buildId));
        } catch (TaskRejectedException exception) {
            stateService.fail(tenantId, buildId, "QUEUE_FULL", null);
            throw new BuildQueueFullException();
        }
    }

    private void run(UUID tenantId, UUID buildId) {
        try {
            BuildContext context = stateService.start(tenantId, buildId);
            BuildExecutionResult result = buildExecutor.execute(context);
            stateService.succeed(tenantId, buildId, result);
        } catch (BuildExecutionException exception) {
            stateService.fail(
                    tenantId,
                    buildId,
                    exception.failureCode(),
                    exception.logStorageKey());
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Build {0} failed with {1}.",
                    buildId,
                    exception.failureCode());
        } catch (RuntimeException exception) {
            stateService.fail(tenantId, buildId, "BUILD_WORKER_ERROR", null);
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Build {0} failed unexpectedly: {1}",
                    buildId,
                    exception.getClass().getSimpleName());
        }
    }
}
