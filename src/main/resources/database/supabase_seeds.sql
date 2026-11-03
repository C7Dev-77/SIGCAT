-- ============================================================
-- SIGCAT v2.0 — Datos Semilla de Predios Públicos e Institucionales
-- Municipio de Sincelejo, Sucre (Capa Base Inalienable)
-- ============================================================

-- 1. Tabla de referencia de predios públicos y reconocidos
INSERT INTO public.predios_publicos_referencia (nombre_oficial, categoria, entidad_duena, direccion, descripcion)
VALUES
    ('Centro Comercial Guacarí',      'COMERCIAL',      'Grupo Guacarí S.A.',           'Cra 23 No. 20-24, Sincelejo',         'Centro comercial de referencia metropolitana en Sincelejo'),
    ('Centro Comercial Viva',         'COMERCIAL',      'Almacenes Éxito S.A.',         'Cll 17 No. 19-50, Sincelejo',         'Centro comercial principal sobre la troncal urbana'),
    ('Almacén Éxito Sincelejo',       'COMERCIAL',      'Almacenes Éxito S.A.',         'Av. Las Palmas, Sincelejo',           'Superficie de hipermercado y comercio'),
    ('Alcaldía de Sincelejo',         'INSTITUCIONAL',  'Municipio de Sincelejo',       'Cra 20 No. 19-37, Sincelejo',         'Sede de la Administración Municipal de Sincelejo'),
    ('Gobernación de Sucre',          'INSTITUCIONAL',  'Departamento de Sucre',        'Cra 21 No. 19-40, Sincelejo',         'Sede del Gobierno Departamental de Sucre'),
    ('Palacio de Justicia',           'INSTITUCIONAL',  'Rama Judicial de Colombia',    'Cll 19 No. 24-20, Sincelejo',         'Sede de juzgados y tribunales de Sincelejo'),
    ('Hospital Universitario de Sucre','SALUD',         'Gobernación de Sucre',         'Cll 14 No. 18-30, Sincelejo',         'Centro médico de referencia departamental'),
    ('Universidad de Sucre (Puerta Roja)','EDUCATIVO',  'Universidad de Sucre',         'Cra 28 No. 5-267, Sincelejo',         'Sede principal del campus universitario público'),
    ('Parque Santander',              'ESPACIO_PUBLICO','Municipio de Sincelejo',       'Centro Histórico, Sincelejo',         'Plaza central y corazón histórico de la ciudad'),
    ('Catedral San Francisco de Asís','RELIGIOSO',      'Diócesis de Sincelejo',        'Cra 19A No. 19-27, Sincelejo',        'Templo y patrimonio religioso del centro histórico'),
    ('Estadio 20 de Enero',           'ESPACIO_PUBLICO','Municipio de Sincelejo',       'Barrio Mochila, Sincelejo',           'Escenario deportivo de béisbol municipal'),
    ('Estadio Arturo Cumplido Sierra','ESPACIO_PUBLICO','Municipio de Sincelejo',       'Barrio Cortijo, Sincelejo',           'Estadio de fútbol de la ciudad de Sincelejo'),
    ('Terminal de Transportes de Sincelejo','VIAL',     'Terminal de Transportes S.A.', 'Cra 12 No. 7-20, Sincelejo',          'Punto neurálgico de transporte intermunicipal'),
    ('UNIAJS - Univ. Antonio José de Sucre','EDUCATIVO','UNIAJS',                       'Cll 21 No. 14-20, Sincelejo',         'Campus universitario institucional'),
    ('Clínica Santa María',           'SALUD',          'Clínica Santa María S.A.S.',   'Cra 19B No. 17-30, Sincelejo',        'Institución prestadora de salud privada')
ON CONFLICT (nombre_oficial) DO NOTHING;

-- 2. Registrar en la tabla maestra de predios como APROBADO / PUBLICO para bloquear solapamientos
INSERT INTO public.predios (codigo_catastral, nombre, direccion, uso_suelo, estado, tipo, nombre_propietario, observaciones, area_calculada)
VALUES
    ('70001-PUB-001', 'Centro Comercial Guacarí',      'Cra 23 No. 20-24, Sincelejo', 'COMERCIAL',     'PUBLICO', 'COMERCIAL',     'Grupo Guacarí S.A.',           'Predio comercial de referencia. Inalienable.', 9850.50),
    ('70001-PUB-002', 'Centro Comercial Viva',         'Cll 17 No. 19-50, Sincelejo', 'COMERCIAL',     'PUBLICO', 'COMERCIAL',     'Almacenes Éxito S.A.',         'Predio comercial Viva Sincelejo. Inalienable.', 11200.00),
    ('70001-PUB-003', 'Almacén Éxito Sincelejo',       'Av. Las Palmas, Sincelejo',   'COMERCIAL',     'PUBLICO', 'COMERCIAL',     'Almacenes Éxito S.A.',         'Supermercado Éxito Las Palmas.', 7450.25),
    ('70001-PUB-004', 'Alcaldía de Sincelejo',         'Cra 20 No. 19-37, Sincelejo', 'INSTITUCIONAL', 'PUBLICO', 'INSTITUCIONAL', 'Municipio de Sincelejo',       'Sede de gobierno municipal. Inalienable.', 4200.80),
    ('70001-PUB-005', 'Gobernación de Sucre',          'Cra 21 No. 19-40, Sincelejo', 'INSTITUCIONAL', 'PUBLICO', 'INSTITUCIONAL', 'Departamento de Sucre',        'Edificio gubernamental. Inalienable.', 3900.40),
    ('70001-PUB-006', 'Palacio de Justicia',           'Cll 19 No. 24-20, Sincelejo', 'INSTITUCIONAL', 'PUBLICO', 'INSTITUCIONAL', 'Rama Judicial de Colombia',    'Bienes de uso público judicial.', 3150.00),
    ('70001-PUB-007', 'Hospital Universitario de Sucre','Cll 14 No. 18-30, Sincelejo', 'SALUD',         'PUBLICO', 'INSTITUCIONAL', 'Gobernación de Sucre',         'Infraestructura de salud pública.', 14300.00),
    ('70001-PUB-008', 'Universidad de Sucre (Puerta Roja)','Cra 28 No. 5-267, Sincelejo','EDUCATIVO',  'PUBLICO', 'INSTITUCIONAL', 'Universidad de Sucre',         'Campus universitario estatal.', 28500.00),
    ('70001-PUB-009', 'Parque Santander',              'Centro Histórico, Sincelejo', 'ESPACIO_PUBLICO','PUBLICO', 'INSTITUCIONAL', 'Municipio de Sincelejo',       'Espacio público municipal inalienable.', 5600.00),
    ('70001-PUB-010', 'Catedral San Francisco de Asís','Cra 19A No. 19-27, Sincelejo','RELIGIOSO',      'PUBLICO', 'INSTITUCIONAL', 'Diócesis de Sincelejo',        'Patrimonio religioso urbano.', 2800.00),
    ('70001-PUB-011', 'Terminal de Transportes de Sincelejo','Cra 12 No. 7-20, Sincelejo','VIAL',     'PUBLICO', 'VIAL',          'Terminal de Transportes S.A.', 'Infraestructura de transporte intermunicipal.', 18200.00)
ON CONFLICT (codigo_catastral) DO NOTHING;

-- 3. Vértices georreferenciados (WGS84) para renderizar los polígonos de Sincelejo en el mapa
INSERT INTO public.vertices (id_predio, orden, longitud, latitud)
SELECT p.id, v.orden, v.longitud, v.latitud
FROM public.predios p
JOIN (VALUES
    -- Guacarí
    ('70001-PUB-001', 1, -75.3885, 9.3086),
    ('70001-PUB-001', 2, -75.3875, 9.3086),
    ('70001-PUB-001', 3, -75.3875, 9.3094),
    ('70001-PUB-001', 4, -75.3885, 9.3094),
    -- Viva
    ('70001-PUB-002', 1, -75.3915, 9.3136),
    ('70001-PUB-002', 2, -75.3905, 9.3136),
    ('70001-PUB-002', 3, -75.3905, 9.3144),
    ('70001-PUB-002', 4, -75.3915, 9.3144),
    -- Éxito
    ('70001-PUB-003', 1, -75.3995, 9.2966),
    ('70001-PUB-003', 2, -75.3985, 9.2966),
    ('70001-PUB-003', 3, -75.3985, 9.2974),
    ('70001-PUB-003', 4, -75.3995, 9.2974),
    -- Alcaldía
    ('70001-PUB-004', 1, -75.3969, 9.3003),
    ('70001-PUB-004', 2, -75.3961, 9.3003),
    ('70001-PUB-004', 3, -75.3961, 9.3009),
    ('70001-PUB-004', 4, -75.3969, 9.3009),
    -- Gobernación
    ('70001-PUB-005', 1, -75.3959, 9.2992),
    ('70001-PUB-005', 2, -75.3951, 9.2992),
    ('70001-PUB-005', 3, -75.3951, 9.2998),
    ('70001-PUB-005', 4, -75.3959, 9.2998),
    -- Palacio de Justicia
    ('70001-PUB-006', 1, -75.3952, 9.3007),
    ('70001-PUB-006', 2, -75.3944, 9.3007),
    ('70001-PUB-006', 3, -75.3944, 9.3013),
    ('70001-PUB-006', 4, -75.3952, 9.3013),
    -- Hospital Universitario
    ('70001-PUB-007', 1, -75.4025, 9.2926),
    ('70001-PUB-007', 2, -75.4015, 9.2926),
    ('70001-PUB-007', 3, -75.4015, 9.2934),
    ('70001-PUB-007', 4, -75.4025, 9.2934),
    -- Universidad de Sucre
    ('70001-PUB-008', 1, -75.3858, 9.3184),
    ('70001-PUB-008', 2, -75.3842, 9.3184),
    ('70001-PUB-008', 3, -75.3842, 9.3196),
    ('70001-PUB-008', 4, -75.3858, 9.3196),
    -- Parque Santander (Ajustado para preservar corredor vial con la Catedral)
    ('70001-PUB-009', 1, -75.3981, 9.3011),
    ('70001-PUB-009', 2, -75.3973, 9.3011),
    ('70001-PUB-009', 3, -75.3973, 9.3017),
    ('70001-PUB-009', 4, -75.3981, 9.3017),
    -- Catedral San Francisco (Ajustado para preservar corredor vial con el Parque Santander)
    ('70001-PUB-010', 1, -75.3971, 9.3019),
    ('70001-PUB-010', 2, -75.3963, 9.3019),
    ('70001-PUB-010', 3, -75.3963, 9.3025),
    ('70001-PUB-010', 4, -75.3971, 9.3025),
    -- Terminal de Transportes
    ('70001-PUB-011', 1, -75.4056, 9.2875),
    ('70001-PUB-011', 2, -75.4044, 9.2875),
    ('70001-PUB-011', 3, -75.4044, 9.2885),
    ('70001-PUB-011', 4, -75.4056, 9.2885)
) AS v(codigo, orden, longitud, latitud) ON p.codigo_catastral = v.codigo
WHERE NOT EXISTS (
    SELECT 1 FROM public.vertices vt WHERE vt.id_predio = p.id
);

-- 4. Documento legal de soporte de validación catastral oficial
INSERT INTO public.documentos (predio_id, tipo_documento, nombre_archivo, storage_path, storage_url, estado, descripcion)
SELECT p.id, 'RECIBO_VALIDACION', 'Certificado_Oficial_Catastro_Sincelejo.pdf',
       'predio_' || p.id || '/recibo_validacion_oficial.pdf',
       'https://jrgawunaqyhwahfavfeg.supabase.co/storage/v1/object/public/documentos-legales/publicos/recibo_validacion_sincelejo.pdf',
       'APROBADO',
       'Certificación Catastral Oficial expedida por la Dirección de Catastro Municipal de Sincelejo'
FROM public.predios p
WHERE p.codigo_catastral = '70001-PUB-004' -- Alcaldía de Sincelejo
AND NOT EXISTS (
    SELECT 1 FROM public.documentos d WHERE d.predio_id = p.id AND d.tipo_documento = 'RECIBO_VALIDACION'
);
