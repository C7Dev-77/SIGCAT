package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.geometry.ShoelaceCalculator;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;
import com.sigcat.utils.OsmTileManager;
import com.sigcat.utils.ViewNavigator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controlador JavaFX para el levantamiento catastral sobre cartografía de Sincelejo, Sucre.
 * Renderiza OpenStreetMap en Java puro, valida solapamientos en tiempo real y calcula áreas con Shoelace.
 */
public class DibujoController {

    @FXML private Canvas mapaCanvas;
    @FXML private TextField txtNombrePredio;
    @FXML private TextField txtDireccion;
    @FXML private ComboBox<String> cmbUsoSuelo;
    @FXML private ComboBox<String> cmbSectorSincelejo; // Opcional en FXML para saltar entre barrios de Sincelejo
    @FXML private Label lblAreaCalculada;
    @FXML private Label lblPerimetroCalculado;
    @FXML private Label lblCantidadVertices;
    @FXML private Label lblEstadoValidacion;
    @FXML private Button btnGuardarPredio;

    private final List<Vertice> verticesActuales = new ArrayList<>();
    private List<Predio> prediosExistentes = new ArrayList<>();
    private final PredioDAO predioDAO = new PredioDAO();
    private int propietarioActualId = 1;
    private Usuario usuarioActual;

    // Motor de OpenStreetMap centrado por defecto en Sincelejo, Sucre, Colombia
    private OsmTileManager osmTileManager;
    private boolean mostrarMapaSatelitalOSM = true;
    private double lastMouseX;
    private double lastMouseY;
    private double pressStartX;
    private double pressStartY;
    // Flag para distinguir arrastre (pan) de clic simple (vértice)
    private boolean isDragging = false;
    private static final double DRAG_THRESHOLD = 5.0;

    @FXML
    public void initialize() {
        if (cmbUsoSuelo != null) {
            cmbUsoSuelo.getItems().addAll("Residencial", "Comercial", "Industrial", "Institucional", "Mixto");
            cmbUsoSuelo.getSelectionModel().selectFirst();
        }

        // Inicializar motor OSM centrado en Sincelejo (9.30472, -75.39778)
        osmTileManager = new OsmTileManager(this::redibujarMapa);
        osmTileManager.centrarEnSincelejo();

        if (cmbSectorSincelejo != null) {
            cmbSectorSincelejo.getItems().addAll(
                    "Centro Histórico (Parque Santander)",
                    "Sector Norte (Unisucre / Puerta Roja)",
                    "Sector Venecia / Las Mercedes",
                    "Sector Majagual / Pozo de Majagual",
                    "Vista General Urbana Sincelejo"
            );
            cmbSectorSincelejo.getSelectionModel().selectFirst();
            cmbSectorSincelejo.setOnAction(e -> osmTileManager.irASectorSincelejo(cmbSectorSincelejo.getValue()));
        }

        cargarMallaCatastral();
        configurarEventosCanvas();
        actualizarMetricasYValidacion();
        redibujarMapa();

        // Ajuste dinámico de dimensiones de Canvas al contenedor al redimensionar o maximizar
        Platform.runLater(() -> {
            if (mapaCanvas != null && mapaCanvas.getParent() instanceof Pane parentPane) {
                parentPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() > 100) {
                        mapaCanvas.setWidth(newVal.doubleValue());
                        redibujarMapa();
                    }
                });
                parentPane.heightProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() > 100) {
                        mapaCanvas.setHeight(newVal.doubleValue());
                        redibujarMapa();
                    }
                });
                if (parentPane.getWidth() > 100) mapaCanvas.setWidth(parentPane.getWidth());
                if (parentPane.getHeight() > 100) mapaCanvas.setHeight(parentPane.getHeight());
                redibujarMapa();
            }
        });
    }

    private boolean modoAuditor = false;
    private Predio predioEnAuditoria;

    public void activarModoInspeccionFuncionario(Predio predio, Usuario funcionario) {
        this.modoAuditor = true;
        this.usuarioActual = funcionario;
        this.predioEnAuditoria = predio;

        if (predio != null) {
            if (txtNombrePredio != null) {
                txtNombrePredio.setText(predio.getNombre() != null ? predio.getNombre() : "");
                txtNombrePredio.setDisable(true);
            }
            if (txtDireccion != null) {
                txtDireccion.setText(predio.getDireccion() != null ? predio.getDireccion() : "");
                txtDireccion.setDisable(true);
            }
            if (cmbUsoSuelo != null && predio.getUsoSuelo() != null) {
                cmbUsoSuelo.setValue(predio.getUsoSuelo());
                cmbUsoSuelo.setDisable(true);
            }
            if (btnGuardarPredio != null) {
                btnGuardarPredio.setVisible(false);
                btnGuardarPredio.setManaged(false);
            }

            if (predio.getVertices() != null && !predio.getVertices().isEmpty()) {
                verticesActuales.clear();
                verticesActuales.addAll(predio.getVertices());

                // Si son coordenadas geográficas, centrar automáticamente el mapa sobre el predio
                if (esCoordenadaGeografica(predio.getVertices().get(0))) {
                    double avgLon = predio.getVertices().stream().mapToDouble(Vertice::getX).average().orElse(osmTileManager.getCenterLon());
                    double avgLat = predio.getVertices().stream().mapToDouble(Vertice::getY).average().orElse(osmTileManager.getCenterLat());
                    osmTileManager.setCoordenadasCentro(avgLat, avgLon, 17);
                }
            }

            mostrarMensajeEstado("AUDITORÍA DE PREDIO: " + predio.getCodigoCatastral() + " (" + predio.getEstado() + ")", "#3b82f6");
        } else {
            mostrarMensajeEstado("VISOR CARTOGRÁFICO: Malla oficial de Sincelejo (Modo Consulta)", "#3b82f6");
            if (btnGuardarPredio != null) {
                btnGuardarPredio.setVisible(false);
                btnGuardarPredio.setManaged(false);
            }
        }
        actualizarMetricasYValidacion();
        redibujarMapa();
    }

    public void activarModoInspeccionPropietario(Predio predio, Usuario propietario) {
        this.modoAuditor = true;
        this.usuarioActual = propietario;
        this.propietarioActualId = propietario != null ? propietario.getId() : (predio != null ? predio.getPropietarioId() : 1);
        this.predioEnAuditoria = predio;

        if (predio != null) {
            if (txtNombrePredio != null) {
                txtNombrePredio.setText(predio.getNombre() != null ? predio.getNombre() : "");
                txtNombrePredio.setDisable(true);
            }
            if (txtDireccion != null) {
                txtDireccion.setText(predio.getDireccion() != null ? predio.getDireccion() : "");
                txtDireccion.setDisable(true);
            }
            if (cmbUsoSuelo != null && predio.getUsoSuelo() != null) {
                cmbUsoSuelo.setValue(predio.getUsoSuelo());
                cmbUsoSuelo.setDisable(true);
            }
            if (btnGuardarPredio != null) {
                btnGuardarPredio.setVisible(false);
                btnGuardarPredio.setManaged(false);
            }

            if (predio.getVertices() != null && !predio.getVertices().isEmpty()) {
                verticesActuales.clear();
                verticesActuales.addAll(predio.getVertices());

                // Si son coordenadas geográficas, centrar automáticamente el mapa sobre el predio
                if (esCoordenadaGeografica(predio.getVertices().get(0))) {
                    double avgLon = predio.getVertices().stream().mapToDouble(Vertice::getX).average().orElse(osmTileManager.getCenterLon());
                    double avgLat = predio.getVertices().stream().mapToDouble(Vertice::getY).average().orElse(osmTileManager.getCenterLat());
                    osmTileManager.setCoordenadasCentro(avgLat, avgLon, 17);
                }
            }

            String color = "#16a34a";
            if ("PENDIENTE".equalsIgnoreCase(predio.getEstado())) color = "#d97706";
            if ("RECHAZADO".equalsIgnoreCase(predio.getEstado())) color = "#dc2626";

            mostrarMensajeEstado("PREDIO: " + predio.getCodigoCatastral() + " (" + predio.getEstado() + ") — Sincelejo", color);
        }
        actualizarMetricasYValidacion();
        redibujarMapa();
    }

    public void setPropietarioActualId(int propietarioId) {
        this.propietarioActualId = propietarioId;
    }

    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
        if (usuario != null) {
            this.propietarioActualId = usuario.getId();
        }
    }

    public void setPropietario(Usuario usuario) {
        setUsuarioActual(usuario);
    }

    public void setPrediosAprobados(List<?> poligonos) {
        // En el nuevo motor la malla catastral se carga automáticamente desde predioDAO.obtenerPrediosParaValidacionTopologica()
    }

    private void cargarMallaCatastral() {
        prediosExistentes = predioDAO.obtenerPrediosParaValidacionTopologica();
    }

    private boolean esCoordenadaGeografica(Vertice v) {
        if (v == null) return false;
        // En Colombia / Sincelejo: Longitud aprox -75.4 (entre -85 y -65), Latitud aprox 9.3 (entre 0 y 15)
        return (v.getX() >= -180.0 && v.getX() <= 180.0 && v.getY() >= -90.0 && v.getY() <= 90.0);
    }

    private List<Vertice> convertirAVerticesMetros(List<Vertice> lista) {
        if (lista == null || lista.isEmpty()) return lista;
        if (!esCoordenadaGeografica(lista.get(0))) {
            return lista; // Ya eran píxeles o metros locales
        }

        double refLon = lista.get(0).getX();
        double refLat = lista.get(0).getY();
        double metersPerDegLat = 111320.0;
        double metersPerDegLon = 111320.0 * Math.cos(Math.toRadians(refLat));

        List<Vertice> metros = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Vertice v = lista.get(i);
            double mx = (v.getX() - refLon) * metersPerDegLon;
            double my = (v.getY() - refLat) * metersPerDegLat;
            metros.add(new Vertice(mx, my, v.getOrden()));
        }
        return metros;
    }

    private void configurarEventosCanvas() {
        if (mapaCanvas == null) return;

        mapaCanvas.setCursor(Cursor.CROSSHAIR);

        // Al presionar: guardar posición inicial exacta y resetear flag de arrastre
        mapaCanvas.setOnMousePressed((MouseEvent event) -> {
            lastMouseX = event.getX();
            lastMouseY = event.getY();
            pressStartX = event.getX();
            pressStartY = event.getY();
            isDragging = false;
        });

        // Al arrastrar (con clic presionado): desplazamiento panorámico fluido del mapa (Pan)
        mapaCanvas.setOnMouseDragged((MouseEvent event) -> {
            double dx = event.getX() - lastMouseX;
            double dy = event.getY() - lastMouseY;
            double distTotal = Math.hypot(event.getX() - pressStartX, event.getY() - pressStartY);

            if (distTotal > DRAG_THRESHOLD) {
                isDragging = true;
                mapaCanvas.setCursor(Cursor.CLOSED_HAND);
            }

            osmTileManager.desplazarMapa(dx, dy);

            lastMouseX = event.getX();
            lastMouseY = event.getY();
            redibujarMapa();
        });

        // Al soltar el mouse: restaurar cursor de precisión
        mapaCanvas.setOnMouseReleased((MouseEvent event) -> {
            mapaCanvas.setCursor(Cursor.CROSSHAIR);
        });

        // Al hacer clic puntual (sin arrastrar): trazar vértice georreferenciado
        mapaCanvas.setOnMouseClicked((MouseEvent event) -> {
            if (modoAuditor) {
                return; // En modo auditoría del funcionario solo se inspecciona y navega
            }
            if (event.getButton() == MouseButton.PRIMARY && !isDragging) {
                double sx = event.getX();
                double sy = event.getY();
                double w = mapaCanvas.getWidth();
                double h = mapaCanvas.getHeight();

                double[] geo = osmTileManager.screenPixelToGeo(sx, sy, w, h);
                double lon = geo[0];
                double lat = geo[1];

                Vertice nuevoVertice = new Vertice(lon, lat, verticesActuales.size() + 1);
                verticesActuales.add(nuevoVertice);

                actualizarMetricasYValidacion();
                redibujarMapa();
            }
            isDragging = false;
        });

        // Navegación con Scroll: Zoom In / Zoom Out centrado en el mapa
        mapaCanvas.setOnScroll((ScrollEvent event) -> {
            int delta = event.getDeltaY() > 0 ? 1 : -1;
            osmTileManager.cambiarZoom(delta);
            actualizarMetricasYValidacion();
            redibujarMapa();
            event.consume();
        });
    }

    @FXML
    public void centrarMapaEnSincelejo() {
        if (osmTileManager != null) {
            osmTileManager.centrarEnSincelejo();
        }
    }

    private void actualizarMetricasYValidacion() {
        int n = verticesActuales.size();
        if (lblCantidadVertices != null) {
            lblCantidadVertices.setText(String.valueOf(n));
        }

        if (n < 3) {
            if (lblAreaCalculada != null) lblAreaCalculada.setText("0.00 m²");
            if (lblPerimetroCalculado != null) lblPerimetroCalculado.setText("0.00 m");
            if (!modoAuditor) {
                mostrarMensajeEstado("Sincelejo, Sucre | Arrastrar: Mover mapa | Scroll: Zoom | Clic: Trazar vértice (mín. 3)", "#64748b");
                if (btnGuardarPredio != null) btnGuardarPredio.setDisable(true);
            }
            return;
        }

        List<Vertice> verticesParaCalculo = convertirAVerticesMetros(verticesActuales);
        double area = ShoelaceCalculator.calcularArea(verticesParaCalculo);
        double perimetro = ShoelaceCalculator.calcularPerimetro(verticesParaCalculo);

        if (lblAreaCalculada != null) lblAreaCalculada.setText(String.format("%.2f m²", area));
        if (lblPerimetroCalculado != null) lblPerimetroCalculado.setText(String.format("%.2f m", perimetro));

        if (modoAuditor) {
            return;
        }

        if (SolapamientoValidator.tieneAutoInterseccion(verticesActuales)) {
            mostrarMensajeEstado("ERROR: Los linderos del polígono se cruzan entre sí.", "#dc2626");
            if (btnGuardarPredio != null) btnGuardarPredio.setDisable(true);
            return;
        }

        for (Predio existente : prediosExistentes) {
            if (SolapamientoValidator.PoligonosSeSolapan(verticesActuales, existente.getVertices())) {
                mostrarMensajeEstado("ALERTA DE SOLAPAMIENTO: Invade el predio " + existente.getCodigoCatastral(), "#dc2626");
                if (btnGuardarPredio != null) btnGuardarPredio.setDisable(true);
                return;
            }
        }

        mostrarMensajeEstado("Topología válida en catastro de Sincelejo.", "#16a34a");
        if (btnGuardarPredio != null) btnGuardarPredio.setDisable(false);
    }

    public void redibujarMapa() {
        if (mapaCanvas == null) return;
        GraphicsContext gc = mapaCanvas.getGraphicsContext2D();
        double w = mapaCanvas.getWidth();
        double h = mapaCanvas.getHeight();

        if (mostrarMapaSatelitalOSM && osmTileManager != null) {
            osmTileManager.renderizarMapaBase(gc, w, h);
        } else {
            gc.setFill(Color.web("#f8fafc"));
            gc.fillRect(0, 0, w, h);
            gc.setStroke(Color.web("#e2e8f0"));
            gc.setLineWidth(1.0);
            for (int x = 0; x < w; x += 40) gc.strokeLine(x, 0, x, h);
            for (int y = 0; y < h; y += 40) gc.strokeLine(0, y, w, y);
        }

        // 1. Dibujar predios de la malla oficial (aprobados en catastro de Sincelejo)
        for (Predio p : prediosExistentes) {
            if (predioEnAuditoria != null && p.getId() == predioEnAuditoria.getId()) {
                continue;
            }

            List<Vertice> verts = p.getVertices();
            if (verts == null || verts.size() < 3) continue;

            int numV = verts.size();
            double[] xs = new double[numV];
            double[] ys = new double[numV];
            boolean esGeo = esCoordenadaGeografica(verts.get(0));

            for (int i = 0; i < numV; i++) {
                Vertice v = verts.get(i);
                if (esGeo) {
                    double[] screen = osmTileManager.geoToScreenPixel(v.getX(), v.getY(), w, h);
                    xs[i] = screen[0];
                    ys[i] = screen[1];
                } else {
                    xs[i] = v.getX();
                    ys[i] = v.getY();
                }
            }

            if ("APROBADO".equalsIgnoreCase(p.getEstado())) {
                gc.setFill(Color.rgb(37, 99, 235, 0.35));
                gc.setStroke(Color.web("#1d4ed8"));
            } else {
                gc.setFill(Color.rgb(245, 158, 11, 0.35));
                gc.setStroke(Color.web("#b45309"));
            }
            gc.setLineWidth(2.2);
            gc.fillPolygon(xs, ys, numV);
            gc.strokePolygon(xs, ys, numV);

            double labelX, labelY;
            if (esGeo) {
                Vertice geoCentro = ShoelaceCalculator.calcularCentroide(verts);
                double[] screenCentro = osmTileManager.geoToScreenPixel(geoCentro.getX(), geoCentro.getY(), w, h);
                labelX = screenCentro[0];
                labelY = screenCentro[1];
            } else {
                Vertice centro = ShoelaceCalculator.calcularCentroide(verts);
                labelX = centro.getX();
                labelY = centro.getY();
            }
            gc.setFill(Color.web("#0f172a"));
            gc.setFont(Font.font("System", 11));
            gc.fillText(p.getCodigoCatastral(), labelX - 25, labelY);
        }

        // 2. Dibujar polígono en trazado activo o en modo inspección
        if (!verticesActuales.isEmpty()) {
            int n = verticesActuales.size();
            double[] xs = new double[n];
            double[] ys = new double[n];
            boolean esGeo = esCoordenadaGeografica(verticesActuales.get(0));

            for (int i = 0; i < n; i++) {
                Vertice v = verticesActuales.get(i);
                if (esGeo) {
                    double[] screen = osmTileManager.geoToScreenPixel(v.getX(), v.getY(), w, h);
                    xs[i] = screen[0];
                    ys[i] = screen[1];
                } else {
                    xs[i] = v.getX();
                    ys[i] = v.getY();
                }
            }

            boolean hayConflicto = SolapamientoValidator.tieneAutoInterseccion(verticesActuales) ||
                    prediosExistentes.stream()
                            .filter(p -> predioEnAuditoria == null || p.getId() != predioEnAuditoria.getId())
                            .anyMatch(p -> SolapamientoValidator.PoligonosSeSolapan(verticesActuales, p.getVertices()));

            if (n >= 3) {
                if (modoAuditor && predioEnAuditoria != null) {
                    if ("APROBADO".equalsIgnoreCase(predioEnAuditoria.getEstado())) {
                        gc.setFill(Color.rgb(34, 197, 94, 0.40));
                        gc.setStroke(Color.web("#15803d"));
                    } else if ("RECHAZADO".equalsIgnoreCase(predioEnAuditoria.getEstado())) {
                        gc.setFill(Color.rgb(239, 68, 68, 0.40));
                        gc.setStroke(Color.web("#dc2626"));
                    } else {
                        gc.setFill(Color.rgb(245, 158, 11, 0.40));
                        gc.setStroke(Color.web("#d97706"));
                    }
                } else {
                    gc.setFill(hayConflicto ? Color.rgb(239, 68, 68, 0.45) : Color.rgb(34, 197, 94, 0.40));
                    gc.setStroke(hayConflicto ? Color.web("#dc2626") : Color.web("#15803d"));
                }
                gc.setLineWidth(2.8);
                gc.fillPolygon(xs, ys, n);
                gc.strokePolygon(xs, ys, n);
            } else {
                gc.setStroke(Color.web("#15803d"));
                gc.setLineWidth(2.8);
                gc.strokePolygon(xs, ys, n);
            }

            // Dibujar vértices con números de orden
            for (int i = 0; i < n; i++) {
                double vx = xs[i];
                double vy = ys[i];
                gc.setFill(Color.web("#ffffff"));
                gc.fillOval(vx - 5, vy - 5, 10, 10);
                gc.setStroke(Color.web("#0f172a"));
                gc.setLineWidth(2.0);
                gc.strokeOval(vx - 5, vy - 5, 10, 10);

                gc.setFill(Color.web("#0f172a"));
                gc.setFont(Font.font("System", 10));
                gc.fillText(String.valueOf(i + 1), vx + 7, vy - 4);
            }
        }
    }

    @FXML
    public void alternarCapaMapa() {
        this.mostrarMapaSatelitalOSM = !this.mostrarMapaSatelitalOSM;
        redibujarMapa();
    }

    @FXML
    public void limpiarLienzo() {
        verticesActuales.clear();
        actualizarMetricasYValidacion();
        redibujarMapa();
    }

    @FXML
    public void deshacerUltimoVertice() {
        if (!verticesActuales.isEmpty()) {
            verticesActuales.remove(verticesActuales.size() - 1);
            actualizarMetricasYValidacion();
            redibujarMapa();
        }
    }

    @FXML
    public void guardarSolicitudPredio() {
        if (verticesActuales.size() < 3) {
            mostrarMensajeEstado("Debe definir al menos 3 vértices.", "#dc2626");
            return;
        }

        String nombre = txtNombrePredio != null ? txtNombrePredio.getText().trim() : "";
        String direccion = txtDireccion != null ? txtDireccion.getText().trim() : "";

        if (nombre.isEmpty() || direccion.isEmpty()) {
            mostrarMensajeEstado("Complete el nombre y la dirección del predio en Sincelejo.", "#dc2626");
            return;
        }

        Predio nuevo = new Predio();
        // Prefijo 70001 correspondiente al código DANE catastral de Sincelejo, Sucre
        nuevo.setCodigoCatastral("70001-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase());
        nuevo.setNombre(nombre);
        nuevo.setDireccion(direccion + " - Sincelejo, Sucre");
        nuevo.setUsoSuelo(cmbUsoSuelo != null ? cmbUsoSuelo.getValue() : "Residencial");
        List<Vertice> verticesParaCalculo = convertirAVerticesMetros(verticesActuales);
        nuevo.setArea(ShoelaceCalculator.calcularArea(verticesParaCalculo));
        nuevo.setEstado("PENDIENTE");
        nuevo.setPropietarioId(propietarioActualId);
        nuevo.setObservaciones("Levantamiento catastral en Sincelejo, Sucre (Sector: " +
                (cmbSectorSincelejo != null ? cmbSectorSincelejo.getValue() : "Urbano") + ")");
        nuevo.setVertices(new ArrayList<>(verticesActuales));

        int idGenerado = predioDAO.registrarPredio(nuevo);
        if (idGenerado > 0) {
            cargarMallaCatastral();
            limpiarLienzo();
            if (txtNombrePredio != null) txtNombrePredio.clear();
            if (txtDireccion != null) txtDireccion.clear();
            mostrarMensajeEstado("Predio radicado en Sincelejo con código " + nuevo.getCodigoCatastral(), "#16a34a");
        } else {
            mostrarMensajeEstado("Error al guardar el predio en la base de datos.", "#dc2626");
        }
    }

    @FXML
    public void volverAlMenu(ActionEvent event) {
        try {
            Stage stage = ViewNavigator.obtenerStage(event);
            if (modoAuditor || (usuarioActual != null && "FUNCIONARIO".equalsIgnoreCase(usuarioActual.getRol()))) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-funcionario.fxml"));
                Parent root = loader.load();
                MenuFuncionarioController controller = loader.getController();
                if (controller != null && usuarioActual != null) {
                    controller.setUsuario(usuarioActual);
                }
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Consola de Funcionario Catastral");
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-propietario.fxml"));
                Parent root = loader.load();
                MenuPropietarioController controller = loader.getController();
                if (controller != null) {
                    if (usuarioActual != null) {
                        controller.inicializarSesionPropietario(usuarioActual);
                    } else {
                        controller.setPropietarioId(propietarioActualId);
                    }
                }
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Portal del Propietario");
            }
        } catch (IOException e) {
            mostrarMensajeEstado("No se pudo regresar al menú: " + e.getMessage(), "#dc2626");
        }
    }

    private void mostrarMensajeEstado(String texto, String colorHex) {
        if (lblEstadoValidacion != null) {
            lblEstadoValidacion.setText(texto);
            lblEstadoValidacion.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
        }
    }
}
