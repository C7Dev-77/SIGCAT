package com.sigcat.utils;

import com.sigcat.security.PasswordUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Inicializador del esquema de la base de datos local SQLite.
 * Crea las tablas si no existen al arrancar la aplicación y precarga
 * la capa base oficial de Sincelejo (predios públicos inalienables).
 *
 * Responsable: Aldo Ibañez (Persistencia)
 * Actualizado por: Cristian Morales (Arquitecto) — Soporte Supabase, Offline-First y Malla Pública
 */
public class DatabaseInitializer {

    /**
     * Ejecuta el script DDL para crear las tablas del sistema.
     * Es seguro llamarlo múltiples veces (usa CREATE TABLE IF NOT EXISTS).
     */
    public static void initialize() throws SQLException {
        Connection conn = DatabaseConnection.getInstance().getConnection();

        try (Statement stmt = conn.createStatement()) {

            // ─── Tabla: usuarios ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id_usuario  INTEGER PRIMARY KEY AUTOINCREMENT,
                    documento   TEXT    NOT NULL UNIQUE,
                    nombre      TEXT    NOT NULL,
                    password    TEXT    NOT NULL,
                    rol         TEXT    NOT NULL CHECK(rol IN ('PROPIETARIO', 'FUNCIONARIO', 'ADMIN'))
                );
            """);

            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN id INTEGER GENERATED ALWAYS AS (id_usuario) VIRTUAL");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN password_hash TEXT DEFAULT ''");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("""
                    CREATE TRIGGER IF NOT EXISTS trg_sync_user_pass_ins AFTER INSERT ON usuarios
                    FOR EACH ROW WHEN (NEW.password_hash != '' AND (NEW.password IS NULL OR NEW.password = ''))
                    BEGIN
                        UPDATE usuarios SET password = NEW.password_hash WHERE id_usuario = NEW.id_usuario;
                    END;
                """);
                stmt.execute("""
                    CREATE TRIGGER IF NOT EXISTS trg_sync_user_hash_ins AFTER INSERT ON usuarios
                    FOR EACH ROW WHEN (NEW.password != '' AND (NEW.password_hash IS NULL OR NEW.password_hash = ''))
                    BEGIN
                        UPDATE usuarios SET password_hash = NEW.password WHERE id_usuario = NEW.id_usuario;
                    END;
                """);
                stmt.execute("UPDATE usuarios SET password_hash = password WHERE (password_hash IS NULL OR password_hash = '') AND password != ''");
            } catch (SQLException ignored) {}

            // ─── Tabla: predios ────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS predios (
                    id_predio        INTEGER PRIMARY KEY AUTOINCREMENT,
                    propietario_id   INTEGER NOT NULL,
                    area             REAL DEFAULT 0.0,
                    codigo_catastral TEXT,
                    nombre           TEXT,
                    direccion        TEXT,
                    uso_suelo        TEXT,
                    observaciones    TEXT,
                    fecha_revision   TEXT,
                    fecha_registro   TEXT DEFAULT CURRENT_TIMESTAMP,
                    id_funcionario   INTEGER,
                    estado           TEXT NOT NULL DEFAULT 'PENDIENTE',
                    id               INTEGER GENERATED ALWAYS AS (id_predio) VIRTUAL,
                    id_propietario   INTEGER GENERATED ALWAYS AS (propietario_id) VIRTUAL,
                    area_calculada   REAL GENERATED ALWAYS AS (area) VIRTUAL,
                    FOREIGN KEY (propietario_id) REFERENCES usuarios(id_usuario),
                    FOREIGN KEY (id_funcionario) REFERENCES usuarios(id_usuario)
                );
            """);

            try {
                stmt.execute("ALTER TABLE predios ADD COLUMN sincronizado INTEGER DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE predios ADD COLUMN id_remoto INTEGER DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE predios ADD COLUMN tipo TEXT DEFAULT 'PRIVADO'");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE predios ADD COLUMN nombre_propietario TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE predios ADD COLUMN recibo_validacion_url TEXT");
            } catch (SQLException ignored) {}

            // ─── Tabla: vertices ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vertices (
                    id_vertice  INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_predio   INTEGER NOT NULL,
                    orden       INTEGER NOT NULL,
                    coord_x     REAL    NOT NULL,
                    coord_y     REAL    NOT NULL,
                    FOREIGN KEY (id_predio) REFERENCES predios(id_predio)
                        ON DELETE CASCADE
                );
            """);

            // ─── Tabla: documentos (Local) ─────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documentos (
                    id TEXT PRIMARY KEY,
                    predio_id INTEGER NOT NULL,
                    propietario_id TEXT,
                    tipo_documento TEXT NOT NULL,
                    nombre_archivo TEXT NOT NULL,
                    storage_path TEXT NOT NULL,
                    storage_url TEXT,
                    estado TEXT DEFAULT 'PENDIENTE_REVISION',
                    descripcion TEXT,
                    subido_en TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (predio_id) REFERENCES predios(id_predio) ON DELETE CASCADE
                );
            """);

            // ─── Usuarios de prueba (Semilla inicial) ─────────────
            seedUsuario(conn, 1, "1001", "Carlos Pérez (Funcionario)", "123", "FUNCIONARIO");
            seedUsuario(conn, 2, "2002", "Ana Gómez (Propietaria)", "123", "PROPIETARIO");
            seedUsuario(conn, 3, "9999", "Administrador SIGCAT", "123", "ADMIN");

            // ─── Predios Públicos e Institucionales de Sincelejo (Capa Base) ───
            seedPrediosPublicosSincelejo(conn);

            System.out.println("[DB] Esquema y usuarios de prueba inicializados correctamente.");
        }
    }

    /**
     * Precarga los predios públicos y reconocidos de Sincelejo (Guacarí, Viva, Éxito, Alcaldía, etc.)
     * con sus coordenadas reales WGS84 para visualización y bloqueo de solapamientos.
     */
    private static void seedPrediosPublicosSincelejo(Connection conn) {
        String[][] landmarks = {
            {"70001-PUB-001", "Centro Comercial Guacarí", "Cra 23 No. 20-24, Sincelejo", "COMERCIAL", "PUBLICO", "COMERCIAL", "Grupo Guacarí S.A.", "Predio comercial de referencia. Inalienable.", "9850.50"},
            {"70001-PUB-002", "Centro Comercial Viva", "Cll 17 No. 19-50, Sincelejo", "COMERCIAL", "PUBLICO", "COMERCIAL", "Almacenes Éxito S.A.", "Predio comercial Viva Sincelejo. Inalienable.", "11200.00"},
            {"70001-PUB-003", "Almacén Éxito Sincelejo", "Av. Las Palmas, Sincelejo", "COMERCIAL", "PUBLICO", "COMERCIAL", "Almacenes Éxito S.A.", "Supermercado Éxito Las Palmas.", "7450.25"},
            {"70001-PUB-004", "Alcaldía de Sincelejo", "Cra 20 No. 19-37, Sincelejo", "INSTITUCIONAL", "PUBLICO", "INSTITUCIONAL", "Municipio de Sincelejo", "Sede de gobierno municipal. Inalienable.", "4200.80"},
            {"70001-PUB-005", "Gobernación de Sucre", "Cra 21 No. 19-40, Sincelejo", "INSTITUCIONAL", "PUBLICO", "INSTITUCIONAL", "Departamento de Sucre", "Edificio gubernamental. Inalienable.", "3900.40"},
            {"70001-PUB-006", "Palacio de Justicia", "Cll 19 No. 24-20, Sincelejo", "INSTITUCIONAL", "PUBLICO", "INSTITUCIONAL", "Rama Judicial de Colombia", "Bienes de uso público judicial.", "3150.00"},
            {"70001-PUB-007", "Hospital Universitario de Sucre", "Cll 14 No. 18-30, Sincelejo", "SALUD", "PUBLICO", "INSTITUCIONAL", "Gobernación de Sucre", "Infraestructura de salud pública.", "14300.00"},
            {"70001-PUB-008", "Universidad de Sucre (Puerta Roja)", "Cra 28 No. 5-267, Sincelejo", "EDUCATIVO", "PUBLICO", "INSTITUCIONAL", "Universidad de Sucre", "Campus universitario estatal.", "28500.00"},
            {"70001-PUB-009", "Parque Santander", "Centro Histórico, Sincelejo", "ESPACIO_PUBLICO", "PUBLICO", "INSTITUCIONAL", "Municipio de Sincelejo", "Espacio público municipal inalienable.", "5600.00"},
            {"70001-PUB-010", "Catedral San Francisco de Asís", "Cra 19A No. 19-27, Sincelejo", "RELIGIOSO", "PUBLICO", "INSTITUCIONAL", "Diócesis de Sincelejo", "Patrimonio religioso urbano.", "2800.00"},
            {"70001-PUB-011", "Terminal de Transportes de Sincelejo", "Cra 12 No. 7-20, Sincelejo", "VIAL", "PUBLICO", "VIAL", "Terminal de Transportes S.A.", "Infraestructura de transporte intermunicipal.", "18200.00"}
        };

        double[][][] coords = {
            // Guacarí
            {{-75.3885, 9.3086}, {-75.3875, 9.3086}, {-75.3875, 9.3094}, {-75.3885, 9.3094}},
            // Viva
            {{-75.3915, 9.3136}, {-75.3905, 9.3136}, {-75.3905, 9.3144}, {-75.3915, 9.3144}},
            // Éxito
            {{-75.3995, 9.2966}, {-75.3985, 9.2966}, {-75.3985, 9.2974}, {-75.3995, 9.2974}},
            // Alcaldía
            {{-75.3969, 9.3003}, {-75.3961, 9.3003}, {-75.3961, 9.3009}, {-75.3969, 9.3009}},
            // Gobernación
            {{-75.3959, 9.2992}, {-75.3951, 9.2992}, {-75.3951, 9.2998}, {-75.3959, 9.2998}},
            // Palacio de Justicia
            {{-75.3952, 9.3007}, {-75.3944, 9.3007}, {-75.3944, 9.3013}, {-75.3952, 9.3013}},
            // Hospital Universitario
            {{-75.4025, 9.2926}, {-75.4015, 9.2926}, {-75.4015, 9.2934}, {-75.4025, 9.2934}},
            // Universidad de Sucre
            {{-75.3858, 9.3184}, {-75.3842, 9.3184}, {-75.3842, 9.3196}, {-75.3858, 9.3196}},
            // Parque Santander
            {{-75.3979, 9.3012}, {-75.3971, 9.3012}, {-75.3971, 9.3018}, {-75.3979, 9.3018}},
            // Catedral San Francisco
            {{-75.3974, 9.3017}, {-75.3966, 9.3017}, {-75.3966, 9.3023}, {-75.3974, 9.3023}},
            // Terminal de Transportes
            {{-75.4056, 9.2875}, {-75.4044, 9.2875}, {-75.4044, 9.2885}, {-75.4056, 9.2885}}
        };

        String checkSql = "SELECT id_predio FROM predios WHERE codigo_catastral = ?";
        String insertPredioSql = """
            INSERT INTO predios (codigo_catastral, nombre, direccion, uso_suelo, estado, tipo, nombre_propietario, observaciones, area, propietario_id, sincronizado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1)
        """;
        String insertVerticeSql = "INSERT INTO vertices (id_predio, orden, coord_x, coord_y) VALUES (?, ?, ?, ?)";

        for (int i = 0; i < landmarks.length; i++) {
            String[] lm = landmarks[i];
            String codigo = lm[0];
            try (PreparedStatement chk = conn.prepareStatement(checkSql)) {
                chk.setString(1, codigo);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        int predioId = rs.getInt(1);
                        // Asegurar que nombre_propietario y tipo estén actualizados
                        String upd = "UPDATE predios SET nombre_propietario = ?, tipo = ? WHERE id_predio = ?";
                        try (PreparedStatement u = conn.prepareStatement(upd)) {
                            u.setString(1, lm[6]);
                            u.setString(2, lm[5]);
                            u.setInt(3, predioId);
                            u.executeUpdate();
                        }
                        // Verificar si tiene vértices
                        String chkVert = "SELECT 1 FROM vertices WHERE id_predio = ? LIMIT 1";
                        try (PreparedStatement cv = conn.prepareStatement(chkVert)) {
                            cv.setInt(1, predioId);
                            try (ResultSet rsv = cv.executeQuery()) {
                                if (!rsv.next()) {
                                    insertarVerticesArray(conn, predioId, coords[i], insertVerticeSql);
                                }
                            }
                        }
                        continue;
                    }
                }
            } catch (SQLException e) {
                System.err.println("[DB] Error verificando landmark " + codigo + ": " + e.getMessage());
                continue;
            }

            try (PreparedStatement ins = conn.prepareStatement(insertPredioSql, Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, lm[0]);
                ins.setString(2, lm[1]);
                ins.setString(3, lm[2]);
                ins.setString(4, lm[3]);
                ins.setString(5, lm[4]);
                ins.setString(6, lm[5]);
                ins.setString(7, lm[6]);
                ins.setString(8, lm[7]);
                ins.setDouble(9, Double.parseDouble(lm[8]));
                ins.executeUpdate();

                try (ResultSet rs = ins.getGeneratedKeys()) {
                    if (rs.next()) {
                        int idGenerado = rs.getInt(1);
                        insertarVerticesArray(conn, idGenerado, coords[i], insertVerticeSql);
                    }
                }
            } catch (SQLException e) {
                System.err.println("[DB] Error insertando landmark " + codigo + ": " + e.getMessage());
            }
        }
    }

    private static void insertarVerticesArray(Connection conn, int predioId, double[][] vertices, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int j = 0; j < vertices.length; j++) {
                stmt.setInt(1, predioId);
                stmt.setInt(2, j + 1);
                stmt.setDouble(3, vertices[j][0]); // longitud (coord_x)
                stmt.setDouble(4, vertices[j][1]); // latitud (coord_y)
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private static void seedUsuario(Connection conn, int id, String documento, String nombre,
                                    String password, String rol) throws SQLException {
        String checkSql = "SELECT password FROM usuarios WHERE id_usuario = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, id);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    String existingPassword = rs.getString("password");
                    if (existingPassword != null && existingPassword.matches("^\\$2[aby]\\$\\d{2}\\$.*")) {
                        return; // Ya tiene hash BCrypt, respetar
                    }
                    String updateSql = "UPDATE usuarios SET password = ?, documento = ?, nombre = ?, rol = ? WHERE id_usuario = ?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setString(1, PasswordUtil.hashPassword(password));
                        updatePs.setString(2, documento);
                        updatePs.setString(3, nombre);
                        updatePs.setString(4, rol);
                        updatePs.setInt(5, id);
                        updatePs.executeUpdate();
                    }
                    return;
                }
            }
        }

        String insertSql = "INSERT INTO usuarios (id_usuario, documento, nombre, password, rol) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            insertPs.setInt(1, id);
            insertPs.setString(2, documento);
            insertPs.setString(3, nombre);
            insertPs.setString(4, PasswordUtil.hashPassword(password));
            insertPs.setString(5, rol);
            insertPs.executeUpdate();
        }
    }
}
