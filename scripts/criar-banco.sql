-- Criação idempotente do banco e do usuário do FlowTask (executar como superusuário).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'flowtask') THEN
        CREATE ROLE flowtask LOGIN PASSWORD 'flowtask' NOSUPERUSER NOCREATEDB NOCREATEROLE;
        ALTER ROLE flowtask SET timezone TO 'America/Sao_Paulo';
    END IF;
END
$$;

SELECT 'CREATE DATABASE flowtask OWNER flowtask'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'flowtask')\gexec

SELECT 'ALTER DATABASE flowtask OWNER TO flowtask'
WHERE EXISTS (SELECT FROM pg_database WHERE datname = 'flowtask' AND NOT pg_has_role('flowtask', oid, 'member'))\gexec
