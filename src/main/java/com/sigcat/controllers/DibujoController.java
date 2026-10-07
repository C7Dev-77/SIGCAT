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
 * Controlador del Canvas de dibujo de predios (Fase 2 & 3).
 *
 * Responsable: Leider Barreto (UI/UX)
 *
 * Flujo de uso:
 *   1. El MenuPropietarioController carga esta pantalla y llama a setPropietario().
 *   2. Aldo debe llamar a setPrediosAprobados() con los polígonos de la BD antes
 *      de que el usuario empiece a dibujar.
 *   3. El usuario hace clic en el Canvas para ir añadiendo vértices.
 *   4. Al hacer clic cerca del primer punto (círculo naranja), el polígono se cierra
 *      y se calcula el área + se valida el solapamiento con JTS.
 */
public class DibujoController {

    /** Distancia (en píxeles) para considerar que el clic "cerró" el polígono sobre el punto inicial. */
    private static final double UMBRAL_CIERRE_PX = 12.0;
    private static final double RADIO_PUNTO = 4.0;

    @FXML private Canvas canvasDibujo;
    @FXML private Label lblArea;
    @FXML private Label lblEstado;

    private final List<Point2D> vertices = new ArrayList<>();

    /**
     * Nota de integración (Aldo): alimentar esta lista con los predios APROBADOS convertidos a
     * Polygon (usando usando SolapamientoValidator.construirPoligono(...)) antes de mostrar la pantalla.
     * Por ahora queda vacía para poder probar el Canvas independientemente.
     */
    private List<Polygon> prediosAprobados = new ArrayList<>();

    private Usuario propietario;
    private boolean poligonoCerrado = false;

    /** Inyecta el propietario autenticado (llamado desde MenuPropietarioController). */
    public void setPropietario(Usuario propietario) {
        this.propietario = propietario;
        if (propietario != null && lblEstado != null) {
            lblEstado.setText("Propietario: " + propietario.getNombre());
        }
    }

    public Usuario getPropietario() {
        return propietario;
    }

    /** Inyecta los predios aprobados para la validación de solapamiento (llamado desde Aldo). */
    public void setPrediosAprobados(List<Polygon> prediosAprobados) {
        this.prediosAprobados = prediosAprobados;
    }

    @FXML
    private void handleClickCanvas(MouseEvent event) {
        if (poligonoCerrado) {
            return; // Ya se cerró; usar "Reiniciar dibujo" para empezar de nuevo.
        }

        Point2D puntoClic = new Point2D(event.getX(), event.getY());

        // Cierre del polígono si el usuario hace clic cerca del primer vértice (≥3 puntos).
        if (!vertices.isEmpty() && vertices.size() >= 3
                && puntoClic.distance(vertices.get(0)) <= UMBRAL_CIERRE_PX) {
            cerrarPoligono();
            return;
        }

        vertices.add(puntoClic);
        redibujarCanvas();
    }

    private void cerrarPoligono() {
        poligonoCerrado = true;
        redibujarCanvas();

        lblEstado.setText("Procesando...");
        lblEstado.setStyle("-fx-text-fill: #a0a0c0;");
        lblArea.setText("Área: calculando...");
        canvasDibujo.setDisable(true);

        List<Vertice> verticesModelo = VerticeAdapter.desdeCanvas(vertices);

        Task<ResultadoRegistro> tarea = new Task<>() {
            @Override
            protected ResultadoRegistro call() {
                double area = ShoelaceCalculator.calcularArea(verticesModelo);

                Polygon poligonoNuevo = SolapamientoValidator.construirPoligono(verticesModelo);
                boolean solapa = SolapamientoValidator.existeSolapamiento(poligonoNuevo, prediosAprobados);

                if (solapa) {
                    return new ResultadoRegistro(area, true, false, null);
                }

                try {
                    Predio predio = new Predio(propietario.getId());
                    predio.setAreaCalculada(area);
                    predio.setVertices(verticesModelo);
                    new PredioDAO().insertarConVertices(predio);
                    return new ResultadoRegistro(area, false, true, null);
                } catch (SQLException e) {
                    return new ResultadoRegistro(area, false, false, e.getMessage());
                }
            }
        };

                tarea.setOnSucceeded(e -> {
            aplicarResultado(tarea.getValue());
            canvasDibujo.setDisable(false);
        });

        tarea.setOnFailed(e -> {
            lblEstado.setText("⚠ Error inesperado: " + tarea.getException().getMessage());
            lblEstado.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
            canvasDibujo.setDisable(false);
        });

        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Actualiza la interfaz con el resultado del registro. Este método se
     * llama siempre desde el hilo de JavaFX (vía setOnSucceeded), nunca
     * directamente desde el Task.
     */
    private void aplicarResultado(ResultadoRegistro resultado) {
        lblArea.setText(String.format("Área: %.2f px²", resultado.getArea()));

        if (resultado.isSolapa()) {
            lblEstado.setText("⚠ Este predio se solapa con uno ya aprobado. No se puede registrar.");
            lblEstado.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
            dibujarPoligonoCerrado(Color.web("#e94560"));
            return;
        }

        dibujarPoligonoCerrado(Color.web("#4caf50"));

        if (resultado.isGuardadoExitoso()) {
            String propInfo = (propietario != null) ? " (Propietario: " + propietario.getNombre() + ")" : "";
            lblEstado.setText("✔ Sin conflictos" + propInfo + ". Predio registrado, pendiente de aprobación.");
            lblEstado.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
        } else {
            lblEstado.setText("⚠ El predio es válido pero no se pudo guardar: " + resultado.getMensajeError());
            lblEstado.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void handleReiniciar() {
        vertices.clear();
        poligonoCerrado = false;
        lblArea.setText("Área: -");
        lblEstado.setText(propietario != null ? "Propietario: " + propietario.getNombre() : "");
        canvasDibujo.setDisable(false);
        limpiarCanvas();
    }

    /**
     * Regresa al menú del propietario conservando la sesión activa.
     */
    @FXML
    private void handleVolver(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-propietario.fxml"));
            Parent root = loader.load();

            MenuPropietarioController controller = loader.getController();
            controller.setUsuario(this.propietario);

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

    // ─── Dibujo ─────────────────────────────────────────────────────────────

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

        // Resalta el primer punto con un círculo naranja (guía visual para cerrar el polígono).
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
            Point2D actual = vertices.get(i);
            Point2D siguiente = vertices.get((i + 1) % vertices.size());
            gc.strokeLine(actual.getX(), actual.getY(), siguiente.getX(), siguiente.getY());
        }
    }

    private void limpiarCanvas() {
        GraphicsContext gc = canvasDibujo.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasDibujo.getWidth(), canvasDibujo.getHeight());
    }
}
