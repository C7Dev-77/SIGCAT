package com.sigcat.controllers;

import com.sigcat.dao.DocumentoDAO;
import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.PredioDAOSupabase;
import com.sigcat.geometry.ShoelaceCalculator;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.supabase.SupabaseStorageService;
import com.sigcat.utils.OsmTileManager;
import com.sigcat.utils.ViewNavigator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controlador JavaFX para el levantamiento catastral sobre cartografía de Sincelejo, Sucre.
 * Integra OpenStreetMap en Java puro, validación de solapamientos con predios públicos/privados,
 * selección interactiva de polígonos por clic en mapa y consulta de recibos de validación en Supabase Storage.
 */
public class DibujoController {

    @FXML private Canvas mapaCanvas;
    @FXML private TextField txtNombrePredio;
    @FXML private TextField txtDireccion;
    @FXML private ComboBox<String> cmbUsoSuelo;
    @FXML private ComboBox<String> cmbSectorSincelejo;
    @FXML private Label lblAreaCalculada;
    @FXML private Label lblPerimetroCalculado;
    @FXML private Label lblCantidadVertices;
    @FXML private Label lblEstadoValidacion;
    @FXML private Button btnGuardarPredio;

    // Ficha dinámica de consulta predial (al hacer clic en cualquier polígono del mapa)
    @FXML private VBox pnlFichaConsultaPredio;
    @FXML private Label lblConsultaTitulo;
    @FXML private Label lblConsultaNombre;
    @FXML private Label lblConsultaCodigo;
    @FXML private Label lblConsultaEstado;
    @FXML private Label lblConsultaPropietario;
    @FXML private Label lblConsultaDireccion;
    @FXML private Label lblConsultaArea;
    @FXML private Label lblConsultaUso;
    @FXML private Button btnVerReciboValidacion;
    @FXML private Label lblAvisoRecibo;

    private final List<Vertice> verticesActuales = new ArrayList<>();
    private List<Predio> prediosExistentes = new ArrayList<>();
    private final PredioDAO predioDAO = new PredioDAO();
    private final PredioDAOSupabase predioSupabaseDAO = new PredioDAOSupabase();
    private final DocumentoDAO documentoDAO = new DocumentoDAO();
    private final SupabaseStorageService storageService = new SupabaseStorageService();

    private int propietarioActualId = 1;
    private Usuario usuarioActual;
    private Predio predioSeleccionadoConsulta;

    // Motor de OpenStreetMap centrado por defecto en Sincelejo, Sucre, Colombia
    private OsmTileManager osmTileManager;
    private boolean mostrarMapaSatelitalOSM = true;
    private double lastMouseX;
    private double lastMouseY;
    private double pressStartX;
    private double pressStartY;
    private boolean isDragging = false;
    private static final double DRAG_THRESHOLD = 5.0;

    private boolean modoAuditor = false;
    private Predio predioEnAuditoria;

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
            seleccionarYMostrarFichaPredio(predio);
        } else {
            mostrarMensajeEstado("VISOR CARTOGRÁFICO: Malla oficial de Sincelejo (Haga clic en un predio para consultarlo)", "#3b82f6");
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
            seleccionarYMostrarFichaPredio(predio);
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
        return (v.getX() >= -180.0 && v.getX() <= 180.0 && v.getY() >= -90.0 && v.getY() <= 90.0);
    }

    private List<Vertice> convertirAVerticesMetros(List<Vertice> lista) {
        if (lista == null || lista.isEmpty()) return lista;
        if (!esCoordenadaGeografica(lista.get(0))) {
            return lista;
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

        // Al hacer clic puntual (sin arrastrar):
        // Si el clic cae dentro de un predio registrado -> consultar su ficha catastral, dueño y recibo
        // Si cae en terreno libre -> agregar vértice de levantamiento catastral
        mapaCanvas.setOnMouseClicked((MouseEvent event) -> {
            if (event.getButton() == MouseButton.PRIMARY && !isDragging) {
                double sx = event.getX();
                double sy = event.getY();
                double w = mapaCanvas.getWidth();
                double h = mapaCanvas.getHeight();

                double[] geo = osmTileManager.screenPixelToGeo(sx, sy, w, h);
                double lon = geo[0];
                double lat = geo[1];

                // 1. Evaluar si el usuario hizo clic dentro de algún predio de la malla
                Predio predioClickeado = buscarPredioEnMalla(lat, lon);

                if (predioClickeado != null) {
                    seleccionarYMostrarFichaPredio(predioClickeado);
                    return;
                }

                // 2. Si no tocó ningún predio existente y no está en modo auditoría, trazar vértice
                if (!modoAuditor) {
                    if (predioSeleccionadoConsulta != null) {
                        cerrarFichaConsulta();
                    }
                    Vertice nuevoVertice = new Vertice(lon, lat, verticesActuales.size() + 1);
                    verticesActuales.add(nuevoVertice);

                    actualizarMetricasYValidacion();
                    redibujarMapa();
                } else {
                    cerrarFichaConsulta();
                }
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

    /**
     * Busca qué predio de la malla oficial contiene las coordenadas geográficas de clic.
     */
    private Predio buscarPredioEnMalla(double lat, double lon) {
        Vertice clickPto = new Vertice(lon, lat, 0);

        for (Predio p : prediosExistentes) {
            if (p.getVertices() != null && p.getVertices().size() >= 3) {
                if (SolapamientoValidator.puntoDentroDePoligono(clickPto, p.getVertices())) {
                    return p;
                }
            }
        }

        // Si está online y no se encontró localmente, consultar Supabase
        if (DatabaseConnectionSupabase.isOnline()) {
            try {
                return predioSupabaseDAO.buscarPredioEnCoordenada(lat, lon);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Despliega la ficha informativa del predio consultado con dueño y recibo de validación.
     */
    public void seleccionarYMostrarFichaPredio(Predio p) {
        this.predioSeleccionadoConsulta = p;

        if (pnlFichaConsultaPredio != null) {
            pnlFichaConsultaPredio.setVisible(true);
            pnlFichaConsultaPredio.setManaged(true);
        }

        String nombre = p.getNombre() != null ? p.getNombre() : "Predio Sin Denominación";
        String codigo = p.getCodigoCatastral() != null ? p.getCodigoCatastral() : "70001-PEND";
        String estado = p.getEstado() != null ? p.getEstado().toUpperCase() : "PENDIENTE";
        String tipo = p.getTipo() != null ? p.getTipo().toUpperCase() : "PRIVADO";
        String direccion = p.getDireccion() != null ? p.getDireccion() : "Sincelejo, Sucre";
        String uso = p.getUsoSuelo() != null ? p.getUsoSuelo() : "Urbano";
        double area = p.getAreaCalculada() > 0 ? p.getAreaCalculada() : p.getArea();

        // Determinar nombre del propietario
        String propietario = p.getNombrePropietario();
        boolean esPublico = "PUBLICO".equalsIgnoreCase(estado) || "PUBLICO".equalsIgnoreCase(tipo) || "INSTITUCIONAL".equalsIgnoreCase(tipo);
        if (propietario == null || propietario.trim().isEmpty()) {
            if (esPublico) {
                propietario = "Municipio de Sincelejo / Entidad Oficial";
            } else {
                propietario = "Propietario Registrado (ID #" + p.getPropietarioId() + ")";
            }
        }

        if (lblConsultaNombre != null) lblConsultaNombre.setText(nombre);
        if (lblConsultaCodigo != null) lblConsultaCodigo.setText(codigo);
        if (lblConsultaDireccion != null) lblConsultaDireccion.setText(direccion);
        if (lblConsultaPropietario != null) lblConsultaPropietario.setText("👤 " + propietario);
        if (lblConsultaArea != null) lblConsultaArea.setText(String.format("%.2f m²", area));
        if (lblConsultaUso != null) lblConsultaUso.setText(uso);

        if (lblConsultaEstado != null) {
            lblConsultaEstado.setText(esPublico ? "PÚBLICO / INSTITUCIONAL" : estado);
            if (esPublico) {
                lblConsultaEstado.setStyle("-fx-background-color: rgba(92, 107, 192, 0.3); -fx-text-fill: #9fa8da; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6;");
            } else if ("APROBADO".equalsIgnoreCase(estado)) {
                lblConsultaEstado.setStyle("-fx-background-color: rgba(34, 197, 94, 0.3); -fx-text-fill: #4ade80; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6;");
            } else {
                lblConsultaEstado.setStyle("-fx-background-color: rgba(245, 158, 11, 0.3); -fx-text-fill: #fbbf24; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6;");
            }
        }

        // Consultar URL de recibo de validación
        String reciboUrl = p.getReciboValidacionUrl();
        if ((reciboUrl == null || reciboUrl.isEmpty()) && DatabaseConnectionSupabase.isOnline()) {
            try {
                DocumentoLegal doc = documentoDAO.obtenerReciboValidacion(p.getId());
                if (doc != null && doc.getStorageUrl() != null) {
                    reciboUrl = doc.getStorageUrl();
                    p.setReciboValidacionUrl(reciboUrl);
                }
            } catch (Exception ignored) {}
        }

        if (btnVerReciboValidacion != null) {
            if (reciboUrl != null && !reciboUrl.isEmpty()) {
                btnVerReciboValidacion.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                if (lblAvisoRecibo != null) lblAvisoRecibo.setText("✔ Recibo de validación verificado disponible");
            } else {
                btnVerReciboValidacion.setStyle("-fx-background-color: #5C6BC0; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                if (lblAvisoRecibo != null) lblAvisoRecibo.setText("ℹ Sin recibo digitalizado (clic para consultar)");
            }
        }

        mostrarMensajeEstado("CONSULTA CATASTRAL: " + nombre + " (" + codigo + ") | Dueño: " + propietario, "#38bdf8");
        redibujarMapa();
    }

    /**
     * Abre el documento legal o recibo de validación del predio en el visor nativo del sistema.
     */
    @FXML
    public void verReciboPredioConsultado() {
        if (predioSeleccionadoConsulta == null) return;

        String url = predioSeleccionadoConsulta.getReciboValidacionUrl();
        if (url != null && !url.isEmpty()) {
            mostrarMensajeEstado("Descargando recibo oficial desde Supabase Storage...", "#22c55e");
            storageService.descargarYAbrirDocumento(url, "Recibo_" + predioSeleccionadoConsulta.getCodigoCatastral() + ".pdf");
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Recibo de Validación Catastral");
            alert.setHeaderText("Predio: " + predioSeleccionadoConsulta.getNombre());
            alert.setContentText("El predio " + predioSeleccionadoConsulta.getCodigoCatastral() +
                    " (" + predioSeleccionadoConsulta.getEstado() + ") no cuenta aún con un recibo de validación digitalizado en Supabase Storage.\n\n" +
                    "El propietario o funcionario puede adjuntarlo desde el módulo de gestión documental.");
            alert.showAndWait();
        }
    }

    @FXML
    public void cerrarFichaConsulta() {
        this.predioSeleccionadoConsulta = null;
        if (pnlFichaConsultaPredio != null) {
            pnlFichaConsultaPredio.setVisible(false);
            pnlFichaConsultaPredio.setManaged(false);
        }
        actualizarMetricasYValidacion();
        redibujarMapa();
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
            if (!modoAuditor && predioSeleccionadoConsulta == null) {
                mostrarMensajeEstado("Sincelejo, Sucre | Arrastrar: Mover mapa | Scroll: Zoom | Clic en predio: Consultar ficha | Clic libre: Trazar vértice", "#64748b");
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
                boolean esPub = "PUBLICO".equalsIgnoreCase(existente.getEstado()) ||
                                "PUBLICO".equalsIgnoreCase(existente.getTipo()) ||
                                "INSTITUCIONAL".equalsIgnoreCase(existente.getTipo());
                String aviso = esPub
                        ? "ALERTA: Invade predio público/institucional (" + existente.getNombre() + ") — Inalienable."
                        : "ALERTA DE SOLAPAMIENTO: Invade el predio " + existente.getCodigoCatastral();
                mostrarMensajeEstado(aviso, "#dc2626");
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

        // 1. Dibujar predios de la malla oficial (aprobados y públicos reconocidos de Sincelejo)
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

            boolean esPublico = "PUBLICO".equalsIgnoreCase(p.getEstado()) ||
                                "PUBLICO".equalsIgnoreCase(p.getTipo()) ||
                                "INSTITUCIONAL".equalsIgnoreCase(p.getTipo());
            boolean esSeleccionado = (predioSeleccionadoConsulta != null && p.getId() == predioSeleccionadoConsulta.getId());

            if (esPublico) {
                // Color Índigo institucional oficial
                gc.setFill(Color.rgb(92, 107, 192, 0.45));
                gc.setStroke(esSeleccionado ? Color.web("#f59e0b") : Color.web("#3949ab"));
            } else if ("APROBADO".equalsIgnoreCase(p.getEstado())) {
                gc.setFill(Color.rgb(37, 99, 235, 0.35));
                gc.setStroke(esSeleccionado ? Color.web("#f59e0b") : Color.web("#1d4ed8"));
            } else {
                gc.setFill(Color.rgb(245, 158, 11, 0.35));
                gc.setStroke(esSeleccionado ? Color.web("#f59e0b") : Color.web("#b45309"));
            }

            gc.setLineWidth(esSeleccionado ? 4.5 : 2.2);
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
            String etiqueta = esPublico ? "🏛 " + (p.getNombre() != null ? p.getNombre() : p.getCodigoCatastral()) : p.getCodigoCatastral();
            gc.fillText(etiqueta, labelX - 35, labelY);
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

    /**
     * Exporta una captura en alta resolución del plano cartográfico actual a una imagen PNG (Mejora #6).
     */
    @FXML
    public void exportarMapaPNG(ActionEvent event) {
        try {
            javafx.scene.image.WritableImage snapshot = mapaCanvas.snapshot(new javafx.scene.SnapshotParameters(), null);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exportar Plano Catastral - SIGCAT Sincelejo");
            fileChooser.setInitialFileName("Plano_Catastral_Sincelejo_" + System.currentTimeMillis() + ".png");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen PNG (*.png)", "*.png"));

            Stage stage = ViewNavigator.obtenerStage(event);
            if (stage == null && mapaCanvas.getScene() != null) {
                stage = (Stage) mapaCanvas.getScene().getWindow();
            }

            File archivoDestino = fileChooser.showSaveDialog(stage);
            if (archivoDestino != null) {
                java.awt.image.BufferedImage bImage = javafx.embed.swing.SwingFXUtils.fromFXImage(snapshot, null);
                javax.imageio.ImageIO.write(bImage, "png", archivoDestino);
                mostrarMensajeEstado("✔ Plano exportado exitosamente a: " + archivoDestino.getName(), "#10b981");

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Plano Cartográfico Exportado");
                alert.setHeaderText("Archivo PNG Generado");
                alert.setContentText("El plano georreferenciado se ha guardado en:\n" + archivoDestino.getAbsolutePath());
                alert.showAndWait();
            }
        } catch (Exception e) {
            mostrarMensajeEstado("Error al exportar plano: " + e.getMessage(), "#ef4444");
        }
    }

    @FXML
    public void limpiarLienzo() {
        verticesActuales.clear();
        cerrarFichaConsulta();
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
        nuevo.setCodigoCatastral("70001-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase());
        nuevo.setNombre(nombre);
        nuevo.setDireccion(direccion + " - Sincelejo, Sucre");
        nuevo.setUsoSuelo(cmbUsoSuelo != null ? cmbUsoSuelo.getValue() : "Residencial");
        List<Vertice> verticesParaCalculo = convertirAVerticesMetros(verticesActuales);
        nuevo.setArea(ShoelaceCalculator.calcularArea(verticesParaCalculo));
        nuevo.setEstado("PENDIENTE");
        nuevo.setTipo("PRIVADO");
        nuevo.setPropietarioId(propietarioActualId);
        if (usuarioActual != null) {
            nuevo.setNombrePropietario(usuarioActual.getNombre());
        }
        nuevo.setObservaciones("Levantamiento catastral en Sincelejo, Sucre (Sector: " +
                (cmbSectorSincelejo != null ? cmbSectorSincelejo.getValue() : "Urbano") + ")");
        nuevo.setVertices(new ArrayList<>(verticesActuales));

        int idGenerado = predioDAO.registrarPredio(nuevo);
        if (idGenerado > 0) {
            nuevo.setId(idGenerado);
            // Auditoría formal de radicación de predio (Mejora #4)
            new com.sigcat.services.AuditService().auditarCreacionPredio(nuevo, usuarioActual);

            // Si hay conexión a Supabase, subirlo de inmediato
            if (DatabaseConnectionSupabase.isOnline()) {
                new Thread(() -> {
                    try {
                        predioSupabaseDAO.insertarPredio(nuevo, usuarioActual != null ? usuarioActual.getDocumento() : null, nuevo.getNombrePropietario());
                    } catch (Exception ignored) {}
                }).start();
            }

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

            if (usuarioActual != null && "ADMIN".equalsIgnoreCase(usuarioActual.getRol())) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-admin.fxml"));
                Parent root = loader.load();
                MenuAdminController controller = loader.getController();
                if (controller != null) controller.setUsuario(usuarioActual);
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Panel Ejecutivo de Administración Catastral");
                return;
            }

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
