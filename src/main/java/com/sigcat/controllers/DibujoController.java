package com.sigcat.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Polygon;

import com.sigcat.dao.PredioDAO;
import com.sigcat.geometry.ShoelaceCalculator;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.geometry.VerticeAdapter;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Controlador del Canvas de dibujo de predios (Fase 2 y 3).
 *
 * Responsable original:  Leider Barreto (UI/UX)
 * Mejoras de concurrencia y ciclo de vida: Cristian Morales (Arquitecto - Fase 4)
 *
 * Flujo de uso:
 *   1. MenuPropietarioController carga esta pantalla y llama a setPropietario()
 *      y setPrediosAprobados() con los polígonos APROBADOS de la BD.
 *   2. El usuario hace clic en el Canvas para añadir vértices.
 *   3. Al hacer clic cerca del primer punto (círculo naranja), el polígono se cierra
 *      y se lanza un Task en un hilo daemon:
 *        a) Calcula el área con Shoelace.
 *        b) Valida el solapamiento con JTS Topology Suite.
 *        c) Si no hay conflicto, persiste predio + vértices de forma transaccional.
 *   4. Los callbacks setOnSucceeded / setOnFailed actualizan la UI de vuelta en
 *      el FX Application Thread — sin Platform.runLater() manual.
 *
 * Garantías de concurrencia (implementadas en cerrarPoligono):
 *   - prediosAprobados es un List.copyOf() inmutable: lectura thread-safe desde el Task.
 *   - Todos los inputs del Task se capturan como variables locales finales en el FX
 *     thread ANTES de lanzar el hilo, eliminando accesos a estado mutable compartido.
 *   - tareaActiva guarda la referencia al Task activo para cancelarlo en caso de
 *     reinicio o navegación hacia atrás mientras la validación está en curso.
 */
public class DibujoController {

    /** Distancia en píxeles para considerar que el clic "cerró" el polígono. */
    private static final double UMBRAL_CIERRE_PX = 12.0;
    private static final double RADIO_PUNTO      = 4.0;

    @FXML private Canvas canvasDibujo;
    @FXML private Label  lblArea;
    @FXML private Label  lblEstado;

    private final List<Point2D> vertices = new ArrayList<>();

    /**
     * Snapshot inmutable de los predios APROBADOS al abrir la pantalla.
     * Inyectado por MenuPropietarioController antes de mostrar la vista.
     * Se almacena como List.copyOf() para garantizar lectura segura desde el Task.
     */
    private List<Polygon> prediosAprobados = List.of();

    private Usuario propietario;
    private boolean poligonoCerrado = false;

    /**
     * Referencia al Task activo. Permite cancelarlo si el usuario pulsa
     * "Reiniciar" o "Volver" mientras la validación de solapamiento corre
     * en background, evitando escrituras fantasma en la base de datos.
     */
    private Task<ResultadoRegistro> tareaActiva;

    // ─── Inyectores ──────────────────────────────────────────────────────────

    /** Inyecta el propietario autenticado (llamado desde MenuPropietarioController). */
    public void setPropietario(Usuario propietario) {
        this.propietario = propietario;
        if (propietario != null && lblEstado != null) {
            lblEstado.setText("Propietario: " + propietario.getNombre());
        }
    }

    public Usuario getPropietario() { return propietario; }

    /**
     * Recibe la lista de polígonos JTS ya aprobados y la almacena como copia
     * inmutable. Esto garantiza que el Task background pueda iterar la lista
     * sin sincronización explícita, incluso si el caller descarta su referencia.
     *
     * @param prediosAprobados polígonos JTS de los predios APROBADOS en la BD.
     */
    public void setPrediosAprobados(List<Polygon> prediosAprobados) {
        this.prediosAprobados = List.copyOf(prediosAprobados);
    }

    // ─── Evento principal del Canvas ─────────────────────────────────────────

    @FXML
    private void handleClickCanvas(MouseEvent event) {
        if (poligonoCerrado) {
            return; // Polígono ya cerrado; usar "Reiniciar dibujo" para comenzar de nuevo.
        }

        Point2D puntoClic = new Point2D(event.getX(), event.getY());

        // Cierra el polígono si el usuario hace clic cerca del primer vértice (mínimo 3 puntos).
        if (!vertices.isEmpty() && vertices.size() >= 3
                && puntoClic.distance(vertices.get(0)) <= UMBRAL_CIERRE_PX) {
            cerrarPoligono();
            return;
        }

        vertices.add(puntoClic);
        redibujarCanvas();
    }

    // ─── Procesamiento asíncrono ──────────────────────────────────────────────

    /**
     * Cierra el polígono visualmente y delega Shoelace + JTS + JDBC a un Task
     * que corre en un hilo daemon independiente del FX Application Thread.
     *
     * Patrón de seguridad aplicado:
     *   1. Se capturan AQUÍ (FX thread) los inputs del Task como variables finales.
     *   2. El Task solo accede a esas variables locales — nunca a campos de la clase.
     *   3. prediosAprobados ya es inmutable (List.copyOf), por lo que no hay
     *      necesidad de sincronización adicional.
     */
    private void cerrarPoligono() {
        poligonoCerrado = true;
        redibujarCanvas();

        lblEstado.setText("Procesando…");
        lblEstado.setStyle("-fx-text-fill: #a0a0c0;");
        lblArea.setText("Área: calculando…");
        canvasDibujo.setDisable(true);

        // ── Captura inmutable de inputs antes de salir del FX thread ──────────
        final List<Vertice> verticesModelo    = VerticeAdapter.desdeCanvas(vertices);
        final List<Polygon> snapshotAprobados = prediosAprobados; // List.copyOf(), thread-safe
        final int           idPropietario     = propietario.getId();
        final String        nombrePropietario = propietario.getNombre();

        // ── Definición del Task ───────────────────────────────────────────────
        Task<ResultadoRegistro> tarea = new Task<>() {
            @Override
            protected ResultadoRegistro call() throws Exception {
                // Paso A: Cálculo del área (puramente computacional, sin I/O)
                double area = ShoelaceCalculator.calcularArea(verticesModelo);

                // Paso B: Construcción del polígono JTS y validación de solapamiento
                Polygon poligonoNuevo = SolapamientoValidator.construirPoligono(verticesModelo);
                boolean solapa = SolapamientoValidator.existeSolapamiento(
                        poligonoNuevo, snapshotAprobados);

                if (solapa) {
                    return new ResultadoRegistro(area, true, false, null);
                }

                // Paso C: Persistencia transaccional (solo si no hay solapamiento)
                Predio predio = new Predio(idPropietario);
                predio.setAreaCalculada(area);
                predio.setVertices(verticesModelo);
                new PredioDAO().insertarConVertices(predio);
                return new ResultadoRegistro(area, false, true, null);
            }
        };

        // ── Callbacks en el FX Application Thread (garantizado por JavaFX) ───
        tarea.setOnSucceeded(e -> {
            aplicarResultado(tarea.getValue(), nombrePropietario);
            canvasDibujo.setDisable(false);
        });

        tarea.setOnCancelled(e -> {
            // El usuario reinició o volvió mientras procesábamos — no hacer nada.
            canvasDibujo.setDisable(false);
        });

        tarea.setOnFailed(e -> {
            Throwable causa = tarea.getException();
            String detalle = (causa instanceof SQLException)
                    ? "Error de base de datos: " + causa.getMessage()
                    : "Error inesperado: " + causa.getMessage();
            lblEstado.setText("⚠ " + detalle);
            lblEstado.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
            canvasDibujo.setDisable(false);
        });

        // ── Lanzar en hilo daemon con nombre descriptivo ──────────────────────
        tareaActiva = tarea;
        Thread hilo = new Thread(tarea, "sigcat-validador");
        hilo.setDaemon(true); // el JVM puede terminar aunque este hilo siga vivo
        hilo.start();
    }

    /**
     * Actualiza los controles visuales con el resultado del Task.
     * Solo se llama desde setOnSucceeded, que garantiza ejecución en el FX Thread.
     *
     * @param resultado       resultado calculado por el Task.
     * @param nombrePropietario nombre capturado antes de lanzar el Task (thread-safe).
     */
    private void aplicarResultado(ResultadoRegistro resultado, String nombrePropietario) {
        lblArea.setText(String.format("Área: %.2f px²", resultado.getArea()));

        if (resultado.isSolapa()) {
            lblEstado.setText("⚠ Este predio se solapa con uno ya aprobado. No se puede registrar.");
            lblEstado.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
            dibujarPoligonoCerrado(Color.web("#e94560"));
            return;
        }

        dibujarPoligonoCerrado(Color.web("#4caf50"));

        if (resultado.isGuardadoExitoso()) {
            lblEstado.setText("✔ Sin conflictos (Propietario: " + nombrePropietario
                    + "). Predio registrado, pendiente de aprobación.");
            lblEstado.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
        } else {
            lblEstado.setText("⚠ El predio es válido pero no se pudo guardar: "
                    + resultado.getMensajeError());
            lblEstado.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
        }
    }

    // ─── Acciones de botones ─────────────────────────────────────────────────

    @FXML
    private void handleReiniciar() {
        cancelarTareaActiva();
        vertices.clear();
        poligonoCerrado = false;
        lblArea.setText("Área: -");
        lblEstado.setText(propietario != null ? "Propietario: " + propietario.getNombre() : "");
        canvasDibujo.setDisable(false);
        limpiarCanvas();
    }

    /**
     * Regresa al menú del propietario conservando la sesión activa.
     * Cancela el Task en vuelo para evitar escrituras fantasma en la BD.
     */
    @FXML
    private void handleVolver(ActionEvent event) {
        cancelarTareaActiva();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-propietario.fxml"));
            Parent root = loader.load();

            MenuPropietarioController controller = loader.getController();
            controller.setUsuario(propietario);

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            Scene escena = new Scene(root, 800, 600);
            escena.getStylesheets().add(
                getClass().getResource("/styles/main.css").toExternalForm()
            );
            stage.setScene(escena);
            stage.setTitle("SIGCAT - Panel del Propietario");
            stage.setResizable(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─── Utilitarios internos ────────────────────────────────────────────────

    /**
     * Cancela el Task activo si está corriendo.
     * Task.cancel(true) envía interrupción al hilo y evita que setOnSucceeded
     * se ejecute, disparando setOnCancelled en su lugar.
     */
    private void cancelarTareaActiva() {
        if (tareaActiva != null && tareaActiva.isRunning()) {
            tareaActiva.cancel(true);
        }
        tareaActiva = null;
    }

    private void redibujarCanvas() {
        limpiarCanvas();
        GraphicsContext gc = canvasDibujo.getGraphicsContext2D();

        gc.setStroke(Color.STEELBLUE);
        gc.setFill(Color.STEELBLUE);
        gc.setLineWidth(2.0);

        for (int i = 0; i < vertices.size(); i++) {
            Point2D punto = vertices.get(i);
            gc.fillOval(punto.getX() - RADIO_PUNTO, punto.getY() - RADIO_PUNTO,
                    RADIO_PUNTO * 2, RADIO_PUNTO * 2);

            if (i > 0) {
                Point2D anterior = vertices.get(i - 1);
                gc.strokeLine(anterior.getX(), anterior.getY(), punto.getX(), punto.getY());
            }
        }

        // Círculo naranja: guía visual para cerrar el polígono sobre el primer punto.
        if (!vertices.isEmpty()) {
            Point2D primero = vertices.get(0);
            gc.setStroke(Color.ORANGE);
            gc.setLineWidth(2.0);
            gc.strokeOval(primero.getX() - UMBRAL_CIERRE_PX, primero.getY() - UMBRAL_CIERRE_PX,
                    UMBRAL_CIERRE_PX * 2, UMBRAL_CIERRE_PX * 2);
        }
    }

    private void dibujarPoligonoCerrado(Color color) {
        GraphicsContext gc = canvasDibujo.getGraphicsContext2D();
        gc.setStroke(color);
        gc.setLineWidth(3.0);

        for (int i = 0; i < vertices.size(); i++) {
            Point2D actual    = vertices.get(i);
            Point2D siguiente = vertices.get((i + 1) % vertices.size());
            gc.strokeLine(actual.getX(), actual.getY(), siguiente.getX(), siguiente.getY());
        }
    }

    private void limpiarCanvas() {
        GraphicsContext gc = canvasDibujo.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasDibujo.getWidth(), canvasDibujo.getHeight());
    }
}
