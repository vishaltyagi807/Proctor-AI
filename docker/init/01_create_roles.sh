#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" <<-EOSQL

      create role auth_owner
        nologin
        noinherit
        nosuperuser
        nocreatedb
        nocreaterole
        bypassrls;

      create role authenticator
        login
        password '${AUTHENTICATOR_USER_PASSWORD}'
        noinherit
        nosuperuser
        nocreatedb
        nocreaterole
        nobypassrls;

      create role proctor
        login
        password '${APP_USER_PASSWORD}'
        noinherit
        nosuperuser
        nocreatedb
        nocreaterole
        nobypassrls;

      alter role authenticator set search_path = public;
      alter role proctor set search_path = public;

EOSQL
