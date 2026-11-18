-- Apply as a database administrator, then set the LOGIN role's password out
-- of band. Do not connect the application as a superuser or BYPASSRLS role.
CREATE ROLE factori_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOINHERIT NOREPLICATION NOBYPASSRLS;
CREATE ROLE factori_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOINHERIT NOREPLICATION NOBYPASSRLS;
CREATE ROLE factori_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    INHERIT NOREPLICATION NOBYPASSRLS IN ROLE factori_runtime;

GRANT CONNECT ON DATABASE factori TO factori_migrator, factori_runtime;
GRANT USAGE, CREATE ON SCHEMA public TO factori_migrator;
GRANT USAGE ON SCHEMA public TO factori_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE factori_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO factori_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE factori_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO factori_runtime;

-- Configure DATABASE_USERNAME/PASSWORD for factori_app and
-- MIGRATION_DATABASE_USERNAME/PASSWORD for factori_migrator. The migrator
-- owns created schema objects; the runtime app role owns none and has no DDL.
