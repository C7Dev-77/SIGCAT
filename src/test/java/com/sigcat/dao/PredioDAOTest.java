package com.sigcat.dao;

import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseConnection;
import com.sigcat.utils.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sigcat.models.ResumenMensual;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de PredioDAO, cubriendo los casos CP-25 a CP-28
 * de la matriz de casos de prueba de Registro y Aprobación de Predios.
 *
 * Responsable: José Sánchez (QA)
 */
class PredioDAOTest {

    private static PredioDAO predioDAO;

    @BeforeAll
    static void inicializarBaseDeDatos() throws SQLException {
        System.setProperty("sigcat.db", "target/sigcat_test.db");
        DatabaseConnection.reiniciarConexion();
        DatabaseInitializer.initialize();
        predioDAO = new PredioDAO();
    }

    @AfterAll
    static void limpiarBaseDeDatos() throws SQLException {
        DatabaseConnection.reiniciarConexion();
    }

    /**
     * Crea un predio de prueba con un cuadrado simple, asociado al
     * propietario semilla (id 2, Ana Gómez).
     */
    private Predio crearPredioDePrueba(double offsetX, double offsetY) {
        Predio predio = new Predio(2); // id_propietario = 2 (usuario semilla)
        predio.setAreaCalculada(100.0);
        predio.setVertices(List.of(
            new Vertice(0, 1, offsetX, offsetY),
            new Vertice(0, 2, offsetX + 10, offsetY),
            new Vertice(0, 3, offsetX + 10, offsetY + 10),
            new Vertice(0, 4, offsetX, offsetY + 10)
        ));
        return predio;
    }

    @Test
    void insertarConVerticesDebeGuardarPredioYVertices() throws SQLException {
        // CP-25
        Predio predio = crearPredioDePrueba(1000, 1000); // zona alejada para no chocar con otras pruebas

        int idGenerado = predioDAO.insertarConVertices(predio);

        assertTrue(idGenerado > 0, "Debe retornar un id válido mayor a 0");

        VerticeDAO verticeDAO = new VerticeDAO();
        List<Vertice> verticesGuardados = verticeDAO.obtenerPorPredio(idGenerado);
        assertEquals(4, verticesGuardados.size(), "Debe haber guardado los 4 vértices del polígono");
    }

    @Test
    void listarPendientesSoloDebeIncluirEstadoPendiente() throws SQLException {
        // CP-26
        Predio predio = crearPredioDePrueba(2000, 2000);
        int id = predioDAO.insertarConVertices(predio); // queda PENDIENTE por defecto

        List<Predio> pendientes = predioDAO.listarPendientes();

        assertTrue(
            pendientes.stream().anyMatch(p -> p.getId() == id),
            "El predio recién insertado debe aparecer en la lista de pendientes"
        );
        assertTrue(
            pendientes.stream().allMatch(p -> p.getEstadoEnum() == Predio.Estado.PENDIENTE),
            "Todos los predios listados deben estar en estado PENDIENTE"
        );
    }

    @Test
    void actualizarEstadoDebeCambiarAPrrobadoYAsignarFuncionario() throws SQLException {
        // CP-28
        Predio predio = crearPredioDePrueba(3000, 3000);
        int id = predioDAO.insertarConVertices(predio);

        predioDAO.actualizarEstado(id, Predio.Estado.APROBADO, 1); // id_funcionario = 1 (usuario semilla)

        List<Predio> aprobados = predioDAO.listarAprobadosConVertices();
        Predio actualizado = aprobados.stream()
            .filter(p -> p.getId() == id)
            .findFirst()
            .orElseThrow(() -> new AssertionError("El predio debería aparecer como APROBADO"));

        assertEquals(Predio.Estado.APROBADO, actualizado.getEstadoEnum());
    }

    @Test
    void listarAprobadosConVerticesDebeTraerLosVertices() throws SQLException {
        // CP-27
        Predio predio = crearPredioDePrueba(4000, 4000);
        int id = predioDAO.insertarConVertices(predio);
        predioDAO.actualizarEstado(id, Predio.Estado.APROBADO, 1);

        List<Predio> aprobados = predioDAO.listarAprobadosConVertices();
        Predio encontrado = aprobados.stream()
            .filter(p -> p.getId() == id)
            .findFirst()
            .orElseThrow();

        assertNotNull(encontrado.getVertices(), "Los vértices no deberían ser null");
        assertEquals(4, encontrado.getVertices().size(), "Debe traer los 4 vértices del predio aprobado");
    }

    @Test
    void obtenerPorEstadoTodasLasSolicitudesDebeRetornarTodos() {
        List<Predio> todos = predioDAO.obtenerTodos();
        List<Predio> filtroTodas = predioDAO.obtenerPorEstado("TODAS LAS SOLICITUDES");
        List<Predio> filtroTodos = predioDAO.obtenerPorEstado("TODOS");

        assertEquals(todos.size(), filtroTodas.size(), "Debe coincidir con la lista completa de predios");
        assertEquals(todos.size(), filtroTodos.size(), "Debe coincidir con la lista completa de predios");
    }

    // ─── Resumen mensual (CP-33) ────────────────────────────────────────────

    /** Fija las fechas de un predio de prueba (las fechas reales las pone la BD al insertar). */
    private void fijarFechas(int id, String fechaRegistro, String fechaRevision) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(
                "UPDATE predios SET fecha_registro = ?, fecha_revision = ? WHERE id_predio = ?")) {
            ps.setString(1, fechaRegistro);
            ps.setString(2, fechaRevision);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    private static ResumenMensual buscarMes(List<ResumenMensual> resumen, int mes) {
        return resumen.stream()
            .filter(r -> r.getMes() == mes)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Falta el mes " + mes + " en el resumen"));
    }

    @Test
    void resumenMensualDebeContarRegistrosYDictamenesPorMes() throws SQLException {
        // Se usa un año de prueba (2019) y se comparan diferencias, para que la
        // prueba no dependa de otros predios que ya existan en la BD de test.
        // Fechas sin hora: no se ven afectadas por la conversión a hora local.
        List<ResumenMensual> antes = predioDAO.resumenMensual(2019);
        ResumenMensual marzoAntes = buscarMes(antes, 3);
        ResumenMensual abrilAntes = buscarMes(antes, 4);

        // Radicado en marzo y sigue pendiente.
        int pendiente = predioDAO.insertarConVertices(crearPredioDePrueba(6000, 6000));
        fijarFechas(pendiente, "2019-03-05", null);

        // Radicado en marzo y aprobado en abril.
        int aprobado = predioDAO.insertarConVertices(crearPredioDePrueba(6100, 6100));
        predioDAO.actualizarEstado(aprobado, "APROBADO", "Prueba resumen");
        fijarFechas(aprobado, "2019-03-20", "2019-04-02");

        // Radicado y rechazado en abril.
        int rechazado = predioDAO.insertarConVertices(crearPredioDePrueba(6200, 6200));
        predioDAO.actualizarEstado(rechazado, "RECHAZADO", "Prueba resumen");
        fijarFechas(rechazado, "2019-04-10", "2019-04-11");

        List<ResumenMensual> resumen = predioDAO.resumenMensual(2019);
        assertEquals(12, resumen.size(), "Con un año seleccionado deben aparecer los 12 meses");

        ResumenMensual marzo = buscarMes(resumen, 3);
        assertEquals(marzoAntes.getRegistrados() + 2, marzo.getRegistrados(), "Marzo: 2 radicados");
        assertEquals(marzoAntes.getPendientes() + 1, marzo.getPendientes(), "Marzo: 1 sigue pendiente");
        assertEquals(marzoAntes.getAprobados(), marzo.getAprobados(), "El aprobado se cuenta en abril, no en marzo");

        ResumenMensual abril = buscarMes(resumen, 4);
        assertEquals(abrilAntes.getRegistrados() + 1, abril.getRegistrados(), "Abril: 1 radicado");
        assertEquals(abrilAntes.getAprobados() + 1, abril.getAprobados(), "Abril: 1 aprobado (radicado en marzo)");
        assertEquals(abrilAntes.getRechazados() + 1, abril.getRechazados(), "Abril: 1 rechazado");
        assertEquals(abrilAntes.getPendientes(), abril.getPendientes());

        assertTrue(predioDAO.aniosConMovimiento().contains(2019));
    }

    @Test
    void resumenMensualNoDebeContarPrediosPublicos() throws SQLException {
        // Los predios públicos de la capa base de Sincelejo no son solicitudes.
        int total = predioDAO.resumenMensual(null).stream().mapToInt(ResumenMensual::getRegistrados).sum();
        long solicitudes = predioDAO.obtenerTodos().stream()
            .filter(p -> !"PUBLICO".equalsIgnoreCase(p.getEstado()))
            .count();
        assertEquals(solicitudes, total, "Registrados debe contar solo solicitudes, no predios públicos");
    }

    @Test
    void fechaConHoraDebeContarseUnaSolaVez() throws SQLException {
        // CURRENT_TIMESTAMP de SQLite está en UTC y el resumen lo pasa a la hora
        // local del equipo: 01:00 UTC del 1 de diciembre es 30 de noviembre en
        // Colombia (UTC-5). Según la zona horaria del equipo que ejecute la prueba
        // cae en uno u otro mes, pero nunca en ambos ni en ninguno.
        List<ResumenMensual> antes = predioDAO.resumenMensual(2018);
        int id = predioDAO.insertarConVertices(crearPredioDePrueba(6300, 6300));
        fijarFechas(id, "2018-12-01 01:00:00", null);

        List<ResumenMensual> despues = predioDAO.resumenMensual(2018);
        int noviembre = buscarMes(despues, 11).getRegistrados() - buscarMes(antes, 11).getRegistrados();
        int diciembre = buscarMes(despues, 12).getRegistrados() - buscarMes(antes, 12).getRegistrados();
        assertEquals(1, noviembre + diciembre, "El predio debe contarse exactamente una vez");
    }
}
