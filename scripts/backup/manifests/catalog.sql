
SELECT json_build_object(
 'schema_owner', (SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='public'),
 'schema_grants',(SELECT json_object_agg(role,json_build_object('usage',has_schema_privilege(role,'public','USAGE'),
                        'create',has_schema_privilege(role,'public','CREATE')))
                  FROM unnest(ARRAY['finsight_app','finsight_migrator','finsight_backup']) role),
 'extensions', (SELECT COALESCE(json_agg(extname ORDER BY extname),'[]') FROM pg_extension WHERE extname <> 'plpgsql'),
 'tables', (SELECT COALESCE(json_object_agg(c.relname, json_build_object(
   'owner',pg_get_userbyid(c.relowner),
   'kind',c.relkind,
   'public_grants',(SELECT COALESCE(json_agg(privilege_type ORDER BY privilege_type),'[]')
                    FROM aclexplode(COALESCE(c.relacl,acldefault('r',c.relowner))) acl WHERE grantee=0),
   'columns',(SELECT json_agg(a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||
                     COALESCE(pg_get_expr(d.adbin,d.adrelid),'')||':'||a.attidentity||':'||a.attgenerated ORDER BY a.attnum)
              FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
              WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),
   'constraints',(SELECT COALESCE(json_object_agg(k.conname,k.contype||':'||pg_get_constraintdef(k.oid)),'{}')
                  FROM pg_constraint k WHERE k.conrelid=c.oid),
   'indexes',(SELECT COALESCE(json_object_agg(i.indexname,i.indexdef),'{}') FROM pg_indexes i
              WHERE i.schemaname='public' AND i.tablename=c.relname),
   'triggers',(SELECT COALESCE(json_object_agg(t.tgname,json_build_object('enabled',t.tgenabled,
                    'definition',pg_get_triggerdef(t.oid))),'{}') FROM pg_trigger t WHERE t.tgrelid=c.oid AND NOT t.tgisinternal),
   'grants',(SELECT json_object_agg(role, ARRAY(SELECT p FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER']) p
                       WHERE has_table_privilege(role,c.oid,p)))
              FROM unnest(ARRAY['finsight_app','finsight_migrator','finsight_backup']) role)
 )),'{}') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m')),
 'sequences',(SELECT COALESCE(json_object_agg(c.relname,json_build_object('owner',pg_get_userbyid(c.relowner),
                 'grants',(SELECT json_object_agg(role,ARRAY(SELECT p FROM unnest(ARRAY['SELECT','USAGE','UPDATE']) p
                          WHERE has_sequence_privilege(role,c.oid,p)))
                    FROM unnest(ARRAY['finsight_app','finsight_migrator','finsight_backup']) role))),'{}')
               FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='S'),
 'functions',(SELECT COALESCE(json_object_agg(p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',
                    json_build_object('owner',pg_get_userbyid(p.proowner),'security_definer',p.prosecdef,
                      'return_type',p.prorettype::regtype::text,
                      'configuration',COALESCE(to_json(p.proconfig),'[]'),
                      'body_sha256',encode(digest(replace(p.prosrc,E'\r\n',E'\n'),'sha256'),'hex'))),'{}')
               FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
                 AND NOT EXISTS(SELECT 1 FROM pg_depend d WHERE d.classid='pg_proc'::regclass AND d.objid=p.oid AND d.deptype='e'))
);
