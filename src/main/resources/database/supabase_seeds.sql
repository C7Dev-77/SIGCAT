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
INSERT INTO public.predios (codigo_catastral, nombre, direccion, uso_suelo, estado, tipo, nombre_propietario, observaciones)
VALUES
    ('70001-PUB-001', 'Centro Comercial Guacarí',      'Cra 23 No. 20-24, Sincelejo', 'COMERCIAL',     'PUBLICO', 'COMERCIAL',     'Grupo Guacarí S.A.',           'Predio oficial comercial. No reclamable por particulares.'),
    ('70001-PUB-002', 'Centro Comercial Viva',         'Cll 17 No. 19-50, Sincelejo', 'COMERCIAL',     'PUBLICO', 'COMERCIAL',     'Almacenes Éxito S.A.',         'Predio oficial comercial. No reclamable por particulares.'),
    ('70001-PUB-003', 'Almacén Éxito Sincelejo',       'Av. Las Palmas, Sincelejo',   'COMERCIAL',     'PUBLICO', 'COMERCIAL',     'Almacenes Éxito S.A.',         'Predio comercial consolidado.'),
    ('70001-PUB-004', 'Alcaldía de Sincelejo',         'Cra 20 No. 19-37, Sincelejo', 'INSTITUCIONAL', 'PUBLICO', 'INSTITUCIONAL', 'Municipio de Sincelejo',       'Sede de gobierno municipal. Inalienable.'),
    ('70001-PUB-005', 'Gobernación de Sucre',          'Cra 21 No. 19-40, Sincelejo', 'INSTITUCIONAL', 'PUBLICO', 'INSTITUCIONAL', 'Departamento de Sucre',        'Edificio gubernamental. Inalienable.'),
    ('70001-PUB-006', 'Palacio de Justicia',           'Cll 19 No. 24-20, Sincelejo', 'INSTITUCIONAL', 'PUBLICO', 'INSTITUCIONAL', 'Rama Judicial de Colombia',    'Bienes de uso público judicial.'),
    ('70001-PUB-007', 'Hospital Universitario de Sucre','Cll 14 No. 18-30, Sincelejo', 'SALUD',         'PUBLICO', 'INSTITUCIONAL', 'Gobernación de Sucre',         'Infraestructura de salud pública.'),
    ('70001-PUB-008', 'Universidad de Sucre (Puerta Roja)','Cra 28 No. 5-267, Sincelejo','EDUCATIVO',  'PUBLICO', 'INSTITUCIONAL', 'Universidad de Sucre',         'Campus universitario estatal.'),
    ('70001-PUB-009', 'Parque Santander',              'Centro Histórico, Sincelejo', 'ESPACIO_PUBLICO','PUBLICO', 'INSTITUCIONAL', 'Municipio de Sincelejo',       'Espacio público municipal inalienable.'),
    ('70001-PUB-010', 'Catedral San Francisco de Asís','Cra 19A No. 19-27, Sincelejo','RELIGIOSO',      'PUBLICO', 'INSTITUCIONAL', 'Diócesis de Sincelejo',        'Patrimonio religioso urbano.'),
    ('70001-PUB-011', 'Terminal de Transportes de Sincelejo','Cra 12 No. 7-20, Sincelejo','VIAL',     'PUBLICO', 'VIAL',          'Terminal de Transportes S.A.', 'Infraestructura de transporte.')
ON CONFLICT (codigo_catastral) DO NOTHING;
