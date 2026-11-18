package com.factori.platform.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;

public final class TenantIdentity {
    private TenantIdentity() {
    }

    public static UUID requireTenantId(Jwt jwt) {
        String claim = jwt.getClaimAsString("tenant_id");
        if (claim == null || claim.isBlank()) {
            throw new AccessDeniedException("The identity token has no tenant_id claim.");
        }
        try {
            return UUID.fromString(claim);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("The identity token has an invalid tenant_id claim.");
        }
    }
}
