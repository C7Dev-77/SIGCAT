package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.geometry.ShoelaceCalculator;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.geometry.VerticeAdapter;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controlador de la pantalla de dibujo de predios (estilo GIS).
 * Los vértices se pueden agregar con clic en el lienzo o escribiendo X e Y.
 * El área se calcula en vivo y el predio se guarda al pulsar "Validar y Enviar".
 *
 * Responsables: Leider Barreto (UI/UX) y Andrés Diaz (lógica de validación)
 */
public class DibujoController implements Initializable {

    private static final double RADIO_NODO = 5.0;
    private static final double TAM_CUADRICULA = 40.0;
    private static final double ZOOM_MIN = 0.4;
    private static final double ZOOM_MAX = 4.0;
    private static final double ZOOM_PASO = 1.25;

    private static final Color AZUL = Color.web("#3b82f6");
    private static final Color ROJO = Color.web("#ef4444");
    private static final Color VERDE = Color.web("#22c55e");
    private static final Color AMBAR = Color.web("#f59e0b");

    private static final String ESTILO_NEUTRO = "-fx-text-fill: #94a3b8;";
    private static final String ESTILO_ERROR = "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
    private static final String ESTILO_OK = "-fx-text-fill: #22c55e; -fx-font-weight: bold;";

    @FXML private Canvas canvasDibujo;
    @FXML private StackPane contenedorCanvas;
    @FXML private TextField txtX;
    @FXML private TextField txtY;
    @FXML private TableView<Point2D> tablaVertices;
    @FXML private TableColumn<Point2D, String> colPunto;
    @FXML private TableColumn<Point2D, Double> colX;
    @FXML private TableColumn<Point2D, Double> colY;
    @FXML private Label lblUsuario;
    @FXML private Label lblIniciales;
    @FXML private Label lblArea;
    @FXML private Label lblEstado;
    @FXML private Button btnEnviar;

    private final ObservableList<Point2D> vertices = FXCollections.observableArrayList();
    private List<Polygon> prediosAprobados = new ArrayList<>();
    private Usuario propietario;

    private double escala = 1.0;
    private boolean enviado = false;
    private Color colorBorde = AZUL;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tablaVertices.setItems(vertices);

        Label vacio = new Label("Aún no hay vértices");
        vacio.setStyle("-fx-text-fill: #64748b;");
        tablaVertices.setPlaceholder(vacio);

        colPunto.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(""));
        colPunto.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : "P" + (getIndex() + 1));
            }
        });
        colX.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getX()));
        colY.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getY()));
        colX.setCellFactory(col -> celdaNumerica());
        colY.setCellFactory(col -> celdaNumerica());

        // El lienzo ocupa todo el espacio del centro
        canvasDibujo.widthProperty().bind(contenedorCanvas.widthProperty());
        canvasDibujo.heightProperty().bind(contenedorCanvas.heightProperty());
        canvasDibujo.widthProperty().addListener((o, a, b) -> redibujar());
        canvasDibujo.heightProperty().addListener((o, a, b) -> redibujar());

        vertices.addListener((ListChangeListener<Point2D>) c -> actualizarVertices());

        txtX.setOnAction(e -> txtY.requestFocus());
        txtY.setOnAction(e -> handleAnadirVertice());

        contenedorCanvas.setOnScroll(e -> {
            if (e.getDeltaY() > 0) {
                handleZoomIn();
            } else if (e.getDeltaY() < 0) {
                handleZoomOut();
            }
        });

        actualizarVertices();
    }

    private TableCell<Point2D, Double> celdaNumerica() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double valor, boolean empty) {
                super.updateItem(valor, empty);
                setText(empty || valor == null ? null : String.format("%.1f", valor));
            }
        };
    }

    // ─── Datos que llegan desde otras pantallas ────────────────────────────

    /** Inyecta el propietario autenticado (llamado desde MenuPropietarioController). */
    public void setPropietario(Usuario propietario) {
        this.propietario = propietario;
        if (propietario != null) {
            lblUsuario.setText(propietario.getNombre());
            lblIniciales.setText(iniciales(propietario.getNombre()));
        }
    }

    public Usuario getPropietario() {
        return propietario;
    }

    /** Inyecta los predios aprobados (se dibujan punteados y se usan para validar solapamiento). */
    public void setPrediosAprobados(List<Polygon> prediosAprobados) {
        this.prediosAprobados = prediosAprobados;
        redibujar();
    }

    private String iniciales(String nombre) {
        StringBuilder sb = new StringBuilder();
        for (String parte : nombre.trim().split("\\s+")) {
            if (parte.isEmpty() || parte.startsWith("(")) {
                continue;
            }
            sb.append(Character.toUpperCase(parte.charAt(0)));
            if (sb.length() == 2) {
                break;
            }
        }
        return sb.toString();
    }

    // ─── Acciones del usuario ──────────────────────────────────────────────

    @FXML
    private void handleClickCanvas(MouseEvent event) {
        if (enviado) {
            return;
        }
        double x = Math.round(event.getX() / escala * 10) / 10.0;
        double y = Math.round(event.getY() / escala * 10) / 10.0;
        agregarVertice(x, y);
    }

    @FXML
    private void handleAnadirVertice() {
        if (enviado) {
            return;
        }
        try {
            double x = Double.parseDouble(txtX.getText().trim().replace(',', '.'));
            double y = Double.parseDouble(txtY.getText().trim().replace(',', '.'));
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new NumberFormatException("valor no finito");
            }
            agregarVertice(x, y);
            txtX.clear();
            txtY.clear();
            txtX.requestFocus();
        } catch (NumberFormatException e) {
            mostrarMensaje("⚠ Escribe números válidos en X e Y.", ESTILO_ERROR);
        }
    }

    private void agregarVertice(double x, double y) {
        colorBorde = AZUL;
        mostrarMensaje("", ESTILO_NEUTRO);
        vertices.add(new Point2D(x, y));
    }

    @FXML
    private void handleDeshacer() {
        if (enviado || vertices.isEmpty()) {
            return;
        }
        colorBorde = AZUL;
        mostrarMensaje("", ESTILO_NEUTRO);
        vertices.remove(vertices.size() - 1);
    }

    @FXML
    private void handleReiniciar() {
        enviado = false;
        colorBorde = AZUL;
        mostrarMensaje("", ESTILO_NEUTRO);
        vertices.clear();
        actualizarVertices();
    }

    @FXML
    private void handleZoomIn() {
        escala = Math.min(ZOOM_MAX, escala * ZOOM_PASO);
        redibujar();
    }

    @FXML
    private void handleZoomOut() {
        escala = Math.max(ZOOM_MIN, escala / ZOOM_PASO);
        redibujar();
    }

    @FXML
    private void handleValidarYEnviar() {
        if (enviado || vertices.size() < 3) {
            return;
        }

        List<Vertice> verticesModelo = VerticeAdapter.desdeCanvas(new ArrayList<>(vertices));
        Polygon poligonoNuevo = SolapamientoValidator.construirPoligono(verticesModelo);

        if (!poligonoNuevo.isValid()) {
            colorBorde = ROJO;
            mostrarMensaje("⚠ Las líneas del polígono se cruzan entre sí. Corrige los vértices.", ESTILO_ERROR);
            redibujar();
            return;
        }

        enviado = true;
        btnEnviar.setDisable(true);
        contenedorCanvas.setDisable(true);
        mostrarMensaje("Procesando...", ESTILO_NEUTRO);

        Task<ResultadoRegistro> tarea = new Task<>() {
            @Override
            protected ResultadoRegistro call() {
                double area = ShoelaceCalculator.calcularArea(verticesModelo);

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
            contenedorCanvas.setDisable(false);
            aplicarResultado(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            contenedorCanvas.setDisable(false);
            enviado = false;
            colorBorde = ROJO;
            Throwable causa = tarea.getException();
            mostrarMensaje("⚠ Error inesperado: " + (causa != null ? causa.getMessage() : "desconocido"), ESTILO_ERROR);
            actualizarVertices();
        });

        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    private void aplicarResultado(ResultadoRegistro resultado) {
        if (resultado.isSolapa()) {
            enviado = false;
            colorBorde = ROJO;
            mostrarMensaje("⚠ Este predio se solapa con uno ya aprobado (contorno punteado). No se registró.", ESTILO_ERROR);
        } else if (resultado.isGuardadoExitoso()) {
            colorBorde = VERDE;
            mostrarMensaje("✔ Predio enviado a catastro. Quedó pendiente de aprobación.", ESTILO_OK);
        } else {
            enviado = false;
            colorBorde = ROJO;
            mostrarMensaje("⚠ El predio es válido pero no se pudo guardar: " + resultado.getMensajeError(), ESTILO_ERROR);
        }
        actualizarVertices();
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

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
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

    // ─── Estado y mensajes ─────────────────────────────────────────────────

    private void actualizarVertices() {
        double area = 0.0;
        if (vertices.size() >= 3) {
            area = ShoelaceCalculator.calcularArea(VerticeAdapter.desdeCanvas(new ArrayList<>(vertices)));
        }
        lblArea.setText(String.format("%.2f px²", area));
        btnEnviar.setDisable(vertices.size() < 3 || enviado);
        redibujar();
    }

    private void mostrarMensaje(String texto, String estilo) {
        lblEstado.setText(texto);
        lblEstado.setStyle(estilo);
    }

    // ─── Dibujo ────────────────────────────────────────────────────────────

    private void redibujar() {
        double ancho = canvasDibujo.getWidth();
        double alto = canvasDibujo.getHeight();
        if (ancho <= 0 || alto <= 0) {
            return;
        }

        GraphicsContext gc = canvasDibujo.getGraphicsContext2D();
        gc.setFill(Color.web("#0b1324"));
        gc.fillRect(0, 0, ancho, alto);

        gc.save();
        gc.scale(escala, escala);
        dibujarCuadricula(gc, ancho / escala, alto / escala);
        dibujarPrediosAprobados(gc);
        dibujarPredioActual(gc);
        gc.restore();

        gc.setFill(Color.web("#64748b"));
        gc.setFont(Font.font(12));
        gc.fillText(String.format("Zoom %d%%", Math.round(escala * 100)), 14, alto - 14);
    }

    private void dibujarCuadricula(GraphicsContext gc, double ancho, double alto) {
        gc.setLineWidth(1.0 / escala);

        int i = 0;
        for (double x = 0; x <= ancho; x += TAM_CUADRICULA, i++) {
            gc.setStroke(i % 5 == 0 ? Color.web("#24324d") : Color.web("#16213a"));
            gc.strokeLine(x, 0, x, alto);
        }

        i = 0;
        for (double y = 0; y <= alto; y += TAM_CUADRICULA, i++) {
            gc.setStroke(i % 5 == 0 ? Color.web("#24324d") : Color.web("#16213a"));
            gc.strokeLine(0, y, ancho, y);
        }
    }

    /** Predios ya aprobados: contorno punteado ámbar de referencia. */
    private void dibujarPrediosAprobados(GraphicsContext gc) {
        gc.setLineWidth(1.5 / escala);
        for (Polygon poligono : prediosAprobados) {
            Coordinate[] coords = poligono.getExteriorRing().getCoordinates();
            double[] xs = new double[coords.length];
            double[] ys = new double[coords.length];
            for (int i = 0; i < coords.length; i++) {
                xs[i] = coords[i].x;
                ys[i] = coords[i].y;
            }

            gc.setFill(Color.web("#f59e0b", 0.1));
            gc.fillPolygon(xs, ys, xs.length);

            gc.setStroke(AMBAR);
            gc.setLineDashes(8 / escala, 6 / escala);
            gc.strokePolygon(xs, ys, xs.length);
            gc.setLineDashes((double[]) null);
        }
    }

    /** Predio que se está dibujando: relleno suave, líneas sólidas y nodos rojos. */
    private void dibujarPredioActual(GraphicsContext gc) {
        int n = vertices.size();
        if (n == 0) {
            return;
        }

        if (n >= 3) {
            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = vertices.get(i).getX();
                ys[i] = vertices.get(i).getY();
            }
            gc.setFill(Color.color(colorBorde.getRed(), colorBorde.getGreen(), colorBorde.getBlue(), 0.15));
            gc.fillPolygon(xs, ys, n);
        }

        gc.setStroke(colorBorde);
        gc.setLineWidth(2.0 / escala);
        for (int i = 0; i < n - 1; i++) {
            Point2D a = vertices.get(i);
            Point2D b = vertices.get(i + 1);
            gc.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
        if (n >= 3) {
            Point2D ultimo = vertices.get(n - 1);
            Point2D primero = vertices.get(0);
            gc.strokeLine(ultimo.getX(), ultimo.getY(), primero.getX(), primero.getY());
        }

        gc.setFont(Font.font(12.0 / escala));
        double radio = RADIO_NODO / escala;
        for (int i = 0; i < n; i++) {
            Point2D p = vertices.get(i);
            gc.setFill(ROJO);
            gc.fillOval(p.getX() - radio, p.getY() - radio, radio * 2, radio * 2);
            gc.setFill(Color.web("#e2e8f0"));
            gc.fillText("P" + (i + 1), p.getX() + 8 / escala, p.getY() - 8 / escala);
        }
    }
}