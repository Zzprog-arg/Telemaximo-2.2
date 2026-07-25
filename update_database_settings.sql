-- =========================================================================
-- SCRIPT DE ACTUALIZACIÓN IPTV PLAYER - CONFIGURACIÓN DE LISTA M3U DINÁMICA
-- =========================================================================
-- Este archivo permite crear la tabla 'settings' e insertar o actualizar
-- la variable 'm3u_url' para que todos tus clientes consuman la lista M3U.
-- Ejecuta este script en la pestaña SQL (SQL Editor) de tu panel Supabase 
-- o en tu gestor de base de datos MySQL de Railway.

-- -------------------------------------------------------------------------
-- OPCIÓN A: Para SUPABASE (PostgreSQL)
-- Ejecuta este código completo en el "SQL Editor" de tu panel de Supabase:
-- -------------------------------------------------------------------------

-- 1. Crear la tabla de configuraciones si no existe
CREATE TABLE IF NOT EXISTS public.settings (
    key_name VARCHAR(150) PRIMARY KEY,
    val_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. Asegurarse de que las peticiones puedan leer/escribir sobre esta tabla (Políticas RLS en Supabase)
ALTER TABLE public.settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Permitir todo a usuarios anonimos sobre settings" ON public.settings;
CREATE POLICY "Permitir todo a usuarios anonimos sobre settings" 
ON public.settings 
FOR ALL 
USING (true) 
WITH CHECK (true);

-- 3. Insertar la lista M3U por defecto o actualizarla si ya existe
INSERT INTO public.settings (key_name, val_value)
VALUES ('m3u_url', 'https://raw.githubusercontent.com/Zzprog-arg/uwu.m3u/fd5dba6c9f6d8cfcccf345aa5c22b71071bee47f/lista2.m3u')
ON CONFLICT (key_name) 
DO UPDATE SET 
    val_value = EXCLUDED.val_value, 
    updated_at = timezone('utc'::text, now());

-- =========================================================================
-- OPCIÓN B: Para RAILWAY / MYSQL RAW JDBC
-- Si en lugar de Supabase utilizas MySQL directo en Railway, usa este bloque:
-- -------------------------------------------------------------------------
/*
CREATE TABLE IF NOT EXISTS settings (
    key_name VARCHAR(150) PRIMARY KEY,
    val_value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO settings (key_name, val_value)
VALUES ('m3u_url', 'https://raw.githubusercontent.com/Zzprog-arg/uwu.m3u/fd5dba6c9f6d8cfcccf345aa5c22b71071bee47f/lista2.m3u')
ON DUPLICATE KEY UPDATE 
    val_value = VALUES(val_value);
*/
