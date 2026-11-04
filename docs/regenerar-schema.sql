-- Genera la guia de columnas del esquema actual (docs/schema.json).
-- Consultar tras cada migracion Flyway.
SELECT jsonb_pretty(jsonb_object_agg(t.table_name, t.def))
FROM (
  SELECT c.table_name,
         jsonb_build_object('columns', jsonb_agg(jsonb_build_object(
           'name', c.column_name, 'type', c.data_type,
           'max_length', c.character_maximum_length,
           'nullable', c.is_nullable = 'YES',
           'default', c.column_default
         ) ORDER BY c.ordinal_position)) AS def
  FROM information_schema.columns c
  WHERE c.table_schema = 'public' AND c.table_name <> 'flyway_schema_history'
  GROUP BY c.table_name
) t;
