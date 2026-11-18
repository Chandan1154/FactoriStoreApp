package com.factori.platform.service;

public final class PlatformExceptions {
    private PlatformExceptions() {
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String resource) {
            super(resource + " was not found.");
        }
    }

    public static class BuildQueueFullException extends RuntimeException {
        public BuildQueueFullException() {
            super("Build capacity is temporarily full.");
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class BuildExecutionException extends RuntimeException {
        private final String failureCode;
        private final String logStorageKey;

        public BuildExecutionException(String failureCode, String logStorageKey, Throwable cause) {
            super("Build failed with code " + failureCode, cause);
            this.failureCode = failureCode;
            this.logStorageKey = logStorageKey;
        }

        public String failureCode() {
            return failureCode;
        }

        public String logStorageKey() {
            return logStorageKey;
        }
    }
}
