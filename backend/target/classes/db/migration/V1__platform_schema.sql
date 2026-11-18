CREATE TABLE retailers (
    id UUID PRIMARY KEY,
    external_retailer_id VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mobile_apps (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    application_id VARCHAR(200) NOT NULL UNIQUE,
    store_name VARCHAR(64) NOT NULL,
    store_url VARCHAR(2048) NOT NULL,
    icon_storage_key VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'BUILDING', 'READY', 'FAILED')),
    version_code INTEGER NOT NULL DEFAULT 1 CHECK (version_code > 0),
    version_name VARCHAR(40) NOT NULL DEFAULT '1.0',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, retailer_id)
);
CREATE INDEX mobile_apps_retailer_created_idx ON mobile_apps (retailer_id, created_at DESC);
CREATE INDEX mobile_apps_retailer_status_idx ON mobile_apps (retailer_id, status);

CREATE TABLE app_builds (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    mobile_app_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'BUILDING', 'SUCCEEDED', 'FAILED')),
    attempt INTEGER NOT NULL DEFAULT 1 CHECK (attempt > 0),
    failure_code VARCHAR(80),
    artifact_storage_key VARCHAR(500),
    artifact_sha256 VARCHAR(64),
    log_storage_key VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_builds_mobile_app_tenant_fk
        FOREIGN KEY (mobile_app_id, retailer_id) REFERENCES mobile_apps(id, retailer_id),
    UNIQUE (id, retailer_id),
    UNIQUE (id, mobile_app_id, retailer_id)
);
CREATE INDEX app_builds_app_created_idx ON app_builds (mobile_app_id, created_at DESC);
CREATE INDEX app_builds_retailer_status_created_idx ON app_builds (retailer_id, status, created_at);

CREATE TABLE app_versions (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    mobile_app_id UUID NOT NULL,
    app_build_id UUID NOT NULL,
    version_code INTEGER NOT NULL CHECK (version_code > 0),
    version_name VARCHAR(40) NOT NULL,
    artifact_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_versions_app_tenant_fk
        FOREIGN KEY (mobile_app_id, retailer_id) REFERENCES mobile_apps(id, retailer_id),
    CONSTRAINT app_versions_build_tenant_fk
        FOREIGN KEY (app_build_id, mobile_app_id, retailer_id)
            REFERENCES app_builds(id, mobile_app_id, retailer_id),
    UNIQUE (mobile_app_id, version_code),
    UNIQUE (app_build_id)
);
CREATE INDEX app_versions_retailer_created_idx ON app_versions (retailer_id, created_at DESC);

CREATE TABLE app_signing_metadata (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    mobile_app_id UUID NOT NULL UNIQUE,
    secret_reference VARCHAR(500) NOT NULL,
    key_alias VARCHAR(200) NOT NULL,
    certificate_sha256 VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_signing_metadata_app_tenant_fk
        FOREIGN KEY (mobile_app_id, retailer_id) REFERENCES mobile_apps(id, retailer_id)
);
CREATE INDEX app_signing_metadata_retailer_idx ON app_signing_metadata (retailer_id);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    external_customer_id VARCHAR(200) NOT NULL,
    consent_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (retailer_id, external_customer_id),
    UNIQUE (id, retailer_id)
);
CREATE INDEX app_users_retailer_consent_idx ON app_users (retailer_id, consent_at);

CREATE TABLE app_installations (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    mobile_app_id UUID NOT NULL,
    app_user_id UUID,
    installation_id_hash BYTEA NOT NULL,
    app_version_code INTEGER NOT NULL,
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uninstalled_at TIMESTAMPTZ,
    CONSTRAINT app_installations_app_tenant_fk
        FOREIGN KEY (mobile_app_id, retailer_id) REFERENCES mobile_apps(id, retailer_id),
    CONSTRAINT app_installations_user_tenant_fk
        FOREIGN KEY (app_user_id, retailer_id) REFERENCES app_users(id, retailer_id),
    UNIQUE (mobile_app_id, installation_id_hash),
    UNIQUE (id, retailer_id),
    UNIQUE (id, mobile_app_id, retailer_id)
);
CREATE INDEX app_installations_retailer_last_seen_idx ON app_installations (retailer_id, last_seen_at DESC);
CREATE INDEX app_installations_user_idx ON app_installations (app_user_id) WHERE app_user_id IS NOT NULL;

CREATE TABLE device_tokens (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    mobile_app_id UUID NOT NULL,
    app_user_id UUID,
    installation_id UUID,
    token_ciphertext BYTEA NOT NULL,
    token_hmac BYTEA NOT NULL,
    encryption_key_reference VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    consent_at TIMESTAMPTZ NOT NULL,
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT device_tokens_app_tenant_fk
        FOREIGN KEY (mobile_app_id, retailer_id) REFERENCES mobile_apps(id, retailer_id),
    CONSTRAINT device_tokens_user_tenant_fk
        FOREIGN KEY (app_user_id, retailer_id) REFERENCES app_users(id, retailer_id),
    CONSTRAINT device_tokens_installation_tenant_fk
        FOREIGN KEY (installation_id, mobile_app_id, retailer_id)
            REFERENCES app_installations(id, mobile_app_id, retailer_id),
    UNIQUE (id, retailer_id),
    UNIQUE (id, mobile_app_id, retailer_id)
);
CREATE UNIQUE INDEX device_tokens_active_hmac_idx
    ON device_tokens (mobile_app_id, token_hmac) WHERE revoked_at IS NULL;
CREATE INDEX device_tokens_retailer_active_idx ON device_tokens (retailer_id, last_refreshed_at)
    WHERE revoked_at IS NULL;

CREATE TABLE notification_campaigns (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    mobile_app_id UUID NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    title VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    deep_link_path VARCHAR(1000),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'COMPLETED', 'CANCELLED')),
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notification_campaigns_app_tenant_fk
        FOREIGN KEY (mobile_app_id, retailer_id) REFERENCES mobile_apps(id, retailer_id),
    UNIQUE (id, retailer_id),
    UNIQUE (id, mobile_app_id, retailer_id)
);
CREATE INDEX notification_campaigns_retailer_created_idx
    ON notification_campaigns (retailer_id, created_at DESC);
CREATE INDEX notification_campaigns_scheduled_idx
    ON notification_campaigns (status, scheduled_at) WHERE status = 'SCHEDULED';

CREATE TABLE notification_delivery_logs (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    campaign_id UUID NOT NULL,
    device_token_id UUID NOT NULL,
    mobile_app_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'INVALID_TOKEN', 'SKIPPED')),
    provider_message_id VARCHAR(200),
    provider_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT notification_delivery_campaign_tenant_fk
        FOREIGN KEY (campaign_id, mobile_app_id, retailer_id)
            REFERENCES notification_campaigns(id, mobile_app_id, retailer_id),
    CONSTRAINT notification_delivery_token_tenant_fk
        FOREIGN KEY (device_token_id, mobile_app_id, retailer_id)
            REFERENCES device_tokens(id, mobile_app_id, retailer_id),
    UNIQUE (campaign_id, device_token_id)
);
CREATE INDEX notification_delivery_retailer_created_idx
    ON notification_delivery_logs (retailer_id, created_at DESC);
CREATE INDEX notification_delivery_campaign_status_idx
    ON notification_delivery_logs (campaign_id, status);

CREATE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER retailers_updated_at BEFORE UPDATE ON retailers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER mobile_apps_updated_at BEFORE UPDATE ON mobile_apps
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER app_builds_updated_at BEFORE UPDATE ON app_builds
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER app_signing_metadata_updated_at BEFORE UPDATE ON app_signing_metadata
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER app_users_updated_at BEFORE UPDATE ON app_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER device_tokens_updated_at BEFORE UPDATE ON device_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER notification_campaigns_updated_at BEFORE UPDATE ON notification_campaigns
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE retailers ENABLE ROW LEVEL SECURITY;
ALTER TABLE mobile_apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_builds ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_signing_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_installations ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_delivery_logs ENABLE ROW LEVEL SECURITY;

ALTER TABLE retailers FORCE ROW LEVEL SECURITY;
ALTER TABLE mobile_apps FORCE ROW LEVEL SECURITY;
ALTER TABLE app_builds FORCE ROW LEVEL SECURITY;
ALTER TABLE app_versions FORCE ROW LEVEL SECURITY;
ALTER TABLE app_signing_metadata FORCE ROW LEVEL SECURITY;
ALTER TABLE app_users FORCE ROW LEVEL SECURITY;
ALTER TABLE app_installations FORCE ROW LEVEL SECURITY;
ALTER TABLE device_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE notification_campaigns FORCE ROW LEVEL SECURITY;
ALTER TABLE notification_delivery_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY retailer_tenant_policy ON retailers
    USING (id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY mobile_apps_tenant_policy ON mobile_apps
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY app_builds_tenant_policy ON app_builds
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY app_versions_tenant_policy ON app_versions
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY app_signing_metadata_tenant_policy ON app_signing_metadata
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY app_users_tenant_policy ON app_users
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY app_installations_tenant_policy ON app_installations
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY device_tokens_tenant_policy ON device_tokens
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY notification_campaigns_tenant_policy ON notification_campaigns
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY notification_delivery_logs_tenant_policy ON notification_delivery_logs
    USING (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (retailer_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
