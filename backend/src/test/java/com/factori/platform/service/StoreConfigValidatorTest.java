package com.factori.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreConfigValidatorTest {
    private final StoreConfigValidator validator = new StoreConfigValidator();

    @Test
    void normalizesHttpsUrlAndCreatesTenantSpecificApplicationIds() {
        UUID firstTenant = UUID.fromString("6d52c77d-a837-4bdb-92d4-fb9b84aa17ea");
        UUID secondTenant = UUID.fromString("71d17d66-593e-4e16-a4cf-3033a9267e10");

        var first = validator.normalize("Zolara", " Shop.Example.com ", firstTenant);
        var second = validator.normalize("Zolara", "https://shop.example.com", secondTenant);

        assertEquals("https://shop.example.com", first.url());
        assertTrue(first.applicationId().matches("com\\.factori\\.store\\.[a-z0-9_]+(\\.[a-z0-9_]+)+"));
        assertNotEquals(first.applicationId(), second.applicationId());
    }

    @Test
    void rejectsInsecurePrivateAndCredentialBearingUrls() {
        assertThrows(
                StoreConfigValidator.InvalidStoreConfigurationException.class,
                () -> validator.normalize("Store", "http://store.example.com"));
        assertThrows(
                StoreConfigValidator.InvalidStoreConfigurationException.class,
                () -> validator.normalize("Store", "https://user:secret@store.example.com"));
        assertThrows(
                StoreConfigValidator.InvalidStoreConfigurationException.class,
                () -> validator.normalize("Store", "https://127.0.0.1"));
        assertThrows(
                StoreConfigValidator.InvalidStoreConfigurationException.class,
                () -> validator.normalize("Store", "https://router.local"));
        assertThrows(
                StoreConfigValidator.InvalidStoreConfigurationException.class,
                () -> validator.normalize("Store", "https://store.example.com:8443"));
    }
}
