package com.sigcat.integration;

import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.PredioDAOSupabase;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.sync.SyncEngine;
import com.sigcat.utils.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración para Fase 5 y Fase 6:
 * - Detección de predios públicos/institucionales por clic geográfico en el mapa.
 * - Validación de dueño y recibo de validación.
 * - Protección contra solapamiento sobre predios públicos inalienables de Sincelejo.
 */
public class ValidacionLegalMapIntegrationTest {

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    @DisplayName("Debe detectar la Alcaldía de Sincelejo por coordenadas geográficas del clic")
    void testBuscarAlcaldiaPorCoordenadas() {
        PredioDAO localDAO = new PredioDAO();
        // Coordenadas en el centro de la Alcaldía de Sincelejo (9.3006, -75.3965)
        double latCentro = 9.3006;
        double lonCentro = -75.3965;

        Predio predio = localDAO.buscarPredioEnCoordenada(latCentro, lonCentro);
        assertNotNull(predio, "Debe encontrar el predio público en la coordenada de la Alcaldía");
        assertEquals("70001-PUB-004", predio.getCodigoCatastral());
        assertEquals("Alcaldía de Sincelejo", predio.getNombre());
        assertEquals("Municipio de Sincelejo", predio.getNombrePropietario());
        assertEquals("PUBLICO", predio.getEstado());
    }

    @Test
    @DisplayName("Debe detectar el Centro Comercial Guacarí por clic en mapa")
    void testBuscarGuacariPorCoordenadas() {
        PredioDAO localDAO = new PredioDAO();
        // Coordenadas en el centro del CC Guacarí (9.3090, -75.3880)
        double latCentro = 9.3090;
        double lonCentro = -75.3880;

        Predio predio = localDAO.buscarPredioEnCoordenada(latCentro, lonCentro);
        assertNotNull(predio, "Debe encontrar el CC Guacarí en el clic");
        assertEquals("70001-PUB-001", predio.getCodigoCatastral());
        assertEquals("Grupo Guacarí S.A.", predio.getNombrePropietario());
    }

    @Test
    @DisplayName("Debe bloquear solapamiento cuando un usuario intenta trazar sobre un predio público")
    void testBloqueoSolapamientoPredioPublico() {
        PredioDAO localDAO = new PredioDAO();
        List<Predio> oficiales = localDAO.obtenerPrediosParaValidacionTopologica();

        // Polígono nuevo que intenta invadir el Parque Santander (-75.3975, 9.3015)
        List<Vertice> invasor = List.of(
            new Vertice(-75.3980, 9.3010, 1),
            new Vertice(-75.3970, 9.3010, 2),
            new Vertice(-75.3970, 9.3020, 3),
            new Vertice(-75.3980, 9.3020, 4)
        );

        boolean conflicto = oficiales.stream()
                .anyMatch(p -> SolapamientoValidator.PoligonosSeSolapan(invasor, p.getVertices()));

        assertTrue(conflicto, "El sistema debe detectar solapamiento ilegal sobre predio público inalienable");
    }

    @Test
    @DisplayName("Debe verificar sincronización bidireccional con Supabase cuando esté online")
    void testSincronizacionOnline() {
        if (DatabaseConnectionSupabase.isOnline()) {
            SyncEngine engine = new SyncEngine();
            SyncEngine.SyncResult res = engine.sincronizar();
            assertTrue(res.exitoso, "La sincronización con Supabase debe completarse sin errores");
            System.out.println("[TEST] Sincronización exitosa: " + res);
        } else {
            System.out.println("[TEST] Supabase offline durante el test, omitiendo prueba de red.");
        }
    }
}
