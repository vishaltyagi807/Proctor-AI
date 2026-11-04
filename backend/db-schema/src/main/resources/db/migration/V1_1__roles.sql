do
$roles$
    begin
        if not exists(select 1 from pg_roles where rolname = 'auth_owner') then
            create role auth_owner nologin noinherit nosuperuser nocreatedb nocreaterole bypassrls;
        end if;

        if not exists(select 1 from pg_roles where rolname = 'authenticator') then
            create role authenticator login noinherit nosuperuser nocreatedb nocreaterole nobypassrls;
        end if;

        if not exists(select 1 from pg_roles where rolname = 'proctor') then
            create role proctor login noinherit nosuperuser nocreatedb nocreaterole nobypassrls;
        end if;

        execute format('alter role authenticator password %L', substr($pw$ ${authenticator_password}$pw$, 2));
        execute format('alter role proctor password %L', substr($pw$ ${app_password}$pw$, 2));
    end
$roles$;

alter role authenticator set search_path = public;
alter role proctor set search_path = public;
