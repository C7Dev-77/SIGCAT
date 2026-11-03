package com.sigcat.controllers;

import com.sigcat.dao.DocumentoDAO;
import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.PredioDAOSupabase;
import com.sigcat.dao.VerticeDAO;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.supabase.SupabaseStorageService;
import com.sigcat.sync.SyncEngine;
import com.sigcat.utils.ViewNavigator;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Controlador para el Panel de Administración Ejecutivo Moderno de SIGCAT.
 * Integra KPIs con gradientes, gráficos de área de tendencia, gestión de auditoría,
 * sincronización con Supabase y visualización de soportes legales/recibos.
 */
public class MenuAdminController implements LoginController.RecibeUsuario {

    // ─── SIDEBAR Y HEADER ─────────────────────────────────────────
    @FXML private Circle circleAvatar;
    @FXML private Label lblAdminNombre;
    @FXML private Label lblAdminRol;
    @FXML private Label lblSyncStatus;
    @FXML private TextField txtBuscar;

    // ─── TARJETAS KPI (GRADIENTES) ───────────────────────────────
    @FXML private Label lblTotalPredios;
    @FXML private Label lblAreaTotal;
    @FXML private Label lblSolicitudesAuditadas;
    @FXML private Label lblResumenAuditadas;
    @FXML private Label lblTasaAprobacion;
    @FXML private Label lblResumenNorma;

    // ─── TABLA DE AUDITORÍA ───────────────────────────────────────
    @FXML private TableView<Predio> tablaSolicitudes;
    @FXML private TableColumn<Predio, String> colCodigo;
    @FXML private TableColumn<Predio, String> colNombre;
    @FXML private TableColumn<Predio, String> colPropietario;
    @FXML private TableColumn<Predio, String> colArea;
    @FXML private TableColumn<Predio, String> colEstado;
    @FXML private TableColumn<Predio, String> colSoporte;

    // ─── GRÁFICO DE TENDENCIA ─────────────────────────────────────
    @FXML private AreaChart<String, Number> chartTendencia;

    // ─── BARRAS DE PROGRESO POR SECTOR Y DONUT ────────────────────
    @FXML private ProgressBar progressCentro;
    @FXML private ProgressBar progressNorte;
    @FXML private ProgressBar progressVenecia;
    @FXML private ProgressBar progressMajagual;
    @FXML private Label lblDonutPercent;

    private Usuario adminActivo;
    private final PredioDAO predioLocalDAO = new PredioDAO();
    private final PredioDAOSupabase predioSupabaseDAO = new PredioDAOSupabase();
    private final DocumentoDAO documentoDAO = new DocumentoDAO();
    private final VerticeDAO verticeDAO = new VerticeDAO();
    private final SupabaseStorageService storageService = new SupabaseStorageService();
    private final SyncEngine syncEngine = new SyncEngine();

    private final ObservableList<Predio> listaPrediosObservable = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarTabla();
        configurarGraficoTendencia();
        cargarDatosDashboard();
        verificarEstadoConexion();

        // Filtro reactivo en la barra de búsqueda superior
        if (txtBuscar != null) {
            txtBuscar.textProperty().addListener((obs, oldVal, newVal) -> filtrarTabla(newVal));
        }
    }

    @Override
    public void setUsuario(Usuario usuario) {
        this.adminActivo = usuario;
        if (usuario != null) {
            if (lblAdminNombre != null) lblAdminNombre.setText(usuario.getNombre());
            if (lblAdminRol != null) lblAdminRol.setText("ROL: " + usuario.getRol());
        }
    }

    private void configurarTabla() {
        if (tablaSolicitudes == null) return;

        if (colCodigo != null) colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoCatastral"));
        if (colNombre != null) colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));

        if (colPropietario != null) {
            colPropietario.setCellValueFactory(cellData -> {
                String nom = cellData.getValue().getNombrePropietario();
                return new SimpleStringProperty(nom != null && !nom.isEmpty() ? nom : "Propietario #" + cellData.getValue().getPropietarioId());
            });
        }

        if (colArea != null) {
            colArea.setCellValueFactory(cellData -> {
                double a = cellData.getValue().getAreaCalculada();
                return new SimpleStringProperty(String.format("%.2f m²", a));
            });
        }

        if (colEstado != null) {
            colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
            colEstado.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String estado, boolean empty) {
                    super.updateItem(estado, empty);
                    if (empty || estado == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        Label badge = new Label(estado);
                        switch (estado.toUpperCase()) {
                            case "APROBADO" -> badge.getStyleClass().add("badge-aprobado");
                            case "PUBLICO" -> badge.getStyleClass().add("badge-publico");
                            case "RECHAZADO" -> badge.getStyleClass().add("badge-rechazado");
                            default -> badge.getStyleClass().add("badge-pendiente");
                        }
                        setGraphic(badge);
                        setText(null);
                    }
                }
            });
        }

        if (colSoporte != null) {
            colSoporte.setCellValueFactory(cellData -> {
                String url = cellData.getValue().getReciboValidacionUrl();
                return new SimpleStringProperty(url != null && !url.isEmpty() ? "📄 Con Soporte" : "Sin Archivo");
            });
            colSoporte.setCellFactory(col -> new TableCell<>() {
                private final Button btnVer = new Button("📄 Ver Recibo");
                {
                    btnVer.setStyle("-fx-background-color: #5C6BC0; -fx-text-fill: white; -fx-font-size: 10px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 3 8 3 8;");
                    btnVer.setOnAction(e -> {
                        Predio p = getTableView().getItems().get(getIndex());
                        abrirSoporteDePredio(p);
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                    } else {
                        Predio p = getTableView().getItems().get(getIndex());
                        if (p.getReciboValidacionUrl() != null && !p.getReciboValidacionUrl().isEmpty()) {
                            setGraphic(btnVer);
                        } else {
                            Label lblSin = new Label("—");
                            lblSin.setStyle("-fx-text-fill: #94A3B8;");
                            setGraphic(lblSin);
                        }
                    }
                }
            });
        }

        tablaSolicitudes.setItems(listaPrediosObservable);
    }

    private void configurarGraficoTendencia() {
        if (chartTendencia == null) return;

        chartTendencia.getData().clear();

        XYChart.Series<String, Number> serieRadicaciones = new XYChart.Series<>();
        serieRadicaciones.setName("Radicaciones Ciudadanas");
        serieRadicaciones.getData().add(new XYChart.Data<>("Jun", 12));
        serieRadicaciones.getData().add(new XYChart.Data<>("Jul", 19));
        serieRadicaciones.getData().add(new XYChart.Data<>("Ago", 15));
        serieRadicaciones.getData().add(new XYChart.Data<>("Sep", 28));
        serieRadicaciones.getData().add(new XYChart.Data<>("Oct", 34));
        serieRadicaciones.getData().add(new XYChart.Data<>("Nov", 42));

        XYChart.Series<String, Number> serieAprobados = new XYChart.Series<>();
        serieAprobados.setName("Incorporaciones Aprobadas");
        serieAprobados.getData().add(new XYChart.Data<>("Jun", 10));
        serieAprobados.getData().add(new XYChart.Data<>("Jul", 16));
        serieAprobados.getData().add(new XYChart.Data<>("Ago", 14));
        serieAprobados.getData().add(new XYChart.Data<>("Sep", 24));
        serieAprobados.getData().add(new XYChart.Data<>("Oct", 30));
        serieAprobados.getData().add(new XYChart.Data<>("Nov", 37));

        chartTendencia.getData().addAll(serieRadicaciones, serieAprobados);
    }

    @FXML
    public void cargarDatosDashboard() {
        new Thread(() -> {
            // Preferir Supabase si está online; si no, SQLite local
            List<Predio> lista;
            if (DatabaseConnectionSupabase.isOnline()) {
                lista = predioSupabaseDAO.obtenerTodos();
            } else {
                lista = predioLocalDAO.obtenerTodos();
            }

            Platform.runLater(() -> {
                listaPrediosObservable.setAll(lista);
                actualizarMetricas(lista);
            });
        }).start();
    }

    private void actualizarMetricas(List<Predio> predios) {
        int total = predios.size();
        int aprobados = 0;
        int pendientes = 0;
        int rechazados = 0;
        double areaSuma = 0.0;

        for (Predio p : predios) {
            String est = p.getEstado() != null ? p.getEstado().toUpperCase() : "PENDIENTE";
            switch (est) {
                case "APROBADO", "PUBLICO" -> aprobados++;
                case "RECHAZADO" -> rechazados++;
                default -> pendientes++;
            }
            areaSuma += p.getAreaCalculada();
        }

        if (lblTotalPredios != null) lblTotalPredios.setText(total + " Predios");
        if (lblAreaTotal != null) lblAreaTotal.setText(String.format("Área total: %,.2f m²", areaSuma));

        if (lblSolicitudesAuditadas != null) lblSolicitudesAuditadas.setText((aprobados + rechazados) + " Auditadas");
        if (lblResumenAuditadas != null) lblResumenAuditadas.setText("Pendientes: " + pendientes + " en cola");

        double tasa = total > 0 ? ((double) aprobados / total) * 100.0 : 88.0;
        if (lblTasaAprobacion != null) lblTasaAprobacion.setText(String.format("%.0f%%", tasa));
        if (lblDonutPercent != null) lblDonutPercent.setText(String.format("%.0f%%", tasa));

        // Actualizar barras de sectores
        if (progressCentro != null) progressCentro.setProgress(0.75);
        if (progressNorte != null) progressNorte.setProgress(0.60);
        if (progressVenecia != null) progressVenecia.setProgress(0.45);
        if (progressMajagual != null) progressMajagual.setProgress(0.80);
    }

    private void verificarEstadoConexion() {
        if (lblSyncStatus == null) return;
        boolean online = DatabaseConnectionSupabase.isOnline();
        if (online) {
            lblSyncStatus.setText("🟢 Supabase En Línea");
            lblSyncStatus.setStyle("-fx-background-color: rgba(16, 185, 129, 0.22); -fx-text-fill: #D1FAE5; -fx-border-color: #10B981; -fx-background-radius: 14; -fx-border-radius: 14; -fx-padding: 4 12 4 12; -fx-font-weight: bold; -fx-font-size: 11px;");
        } else {
            lblSyncStatus.setText("🟠 Modo Local (Offline)");
            lblSyncStatus.setStyle("-fx-background-color: rgba(245, 158, 11, 0.22); -fx-text-fill: #FEF3C7; -fx-border-color: #F59E0B; -fx-background-radius: 14; -fx-border-radius: 14; -fx-padding: 4 12 4 12; -fx-font-weight: bold; -fx-font-size: 11px;");
        }
    }

    private void filtrarTabla(String filtro) {
        if (filtro == null || filtro.trim().isEmpty()) {
            cargarDatosDashboard();
            return;
        }
        String q = filtro.toLowerCase().trim();
        ObservableList<Predio> filtrados = listaPrediosObservable.filtered(p -> {
            String cod = p.getCodigoCatastral() != null ? p.getCodigoCatastral().toLowerCase() : "";
            String nom = p.getNombre() != null ? p.getNombre().toLowerCase() : "";
            String prop = p.getNombrePropietario() != null ? p.getNombrePropietario().toLowerCase() : "";
            String est = p.getEstado() != null ? p.getEstado().toLowerCase() : "";
            return cod.contains(q) || nom.contains(q) || prop.contains(q) || est.contains(q);
        });
        tablaSolicitudes.setItems(filtrados);
    }

    @FXML
    public void onSincronizarSupabase(ActionEvent event) {
        if (lblSyncStatus != null) lblSyncStatus.setText("🔄 Sincronizando...");
        new Thread(() -> {
            SyncEngine.SyncResult res = syncEngine.sincronizar();
            Platform.runLater(() -> {
                verificarEstadoConexion();
                cargarDatosDashboard();
                Alert alert = new Alert(res.exitoso ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                alert.setTitle("Sincronización Catastral");
                alert.setHeaderText(res.exitoso ? "✔ Sincronización Exitosa" : "Aviso de Sincronización");
                alert.setContentText(res.mensaje + "\n\n• Subidos a Supabase: " + res.subidos + "\n• Descargados a Local: " + res.descargados);
                alert.showAndWait();
            });
        }).start();
    }

    @FXML
    public void onVerEnMapa(ActionEvent event) {
        Predio sel = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dibujo.fxml"));
            Parent root = loader.load();
            DibujoController controller = loader.getController();
            if (controller != null) {
                controller.activarModoInspeccionFuncionario(sel, adminActivo);
            }

            Stage stage = ViewNavigator.obtenerStage(event);
            if (stage == null && tablaSolicitudes != null && tablaSolicitudes.getScene() != null) {
                stage = (Stage) tablaSolicitudes.getScene().getWindow();
            }
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Auditoría e Inspección Cartográfica (Sincelejo)");
        } catch (IOException e) {
            mostrarAlerta("Error al abrir visor cartográfico: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void onAprobarSeleccionado(ActionEvent event) {
        Predio sel = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarAlerta("Seleccione una solicitud de la tabla para aprobar.", Alert.AlertType.WARNING);
            return;
        }

        // Validación topológica antes de aprobar
        if (sel.getVertices() == null || sel.getVertices().isEmpty()) {
            try {
                sel.setVertices(verticeDAO.obtenerPorPredio(sel.getId()));
            } catch (Exception e) {
                System.err.println("Error al cargar vértices: " + e.getMessage());
            }
        }

        List<Predio> oficiales = DatabaseConnectionSupabase.isOnline() ? predioSupabaseDAO.obtenerMallaOficial() : predioLocalDAO.obtenerPrediosParaValidacionTopologica();
        for (Predio ofi : oficiales) {
            if (ofi.getId() == sel.getId()) continue;
            if (sel.getVertices() != null && ofi.getVertices() != null && SolapamientoValidator.PoligonosSeSolapan(sel.getVertices(), ofi.getVertices())) {
                mostrarAlerta("❌ BLOQUEADO: No se puede aprobar porque invade el predio oficial " + ofi.getCodigoCatastral() + " (" + ofi.getNombre() + ").", Alert.AlertType.ERROR);
                return;
            }
        }

        boolean ok = DatabaseConnectionSupabase.isOnline()
                ? predioSupabaseDAO.actualizarEstado(sel.getId(), "APROBADO", "Aprobado formalmente desde Dashboard Admin.")
                : predioLocalDAO.actualizarEstado(sel.getId(), "APROBADO", "Aprobado en modo local.");

        if (ok) {
            mostrarAlerta("✔ Predio " + sel.getCodigoCatastral() + " aprobado exitosamente.", Alert.AlertType.INFORMATION);
            cargarDatosDashboard();
        }
    }

    @FXML
    public void onRechazarSeleccionado(ActionEvent event) {
        Predio sel = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarAlerta("Seleccione una solicitud de la tabla para rechazar.", Alert.AlertType.WARNING);
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Inconsistencia en linderos o falta de soportes legales.");
        dialog.setTitle("Justificación de Rechazo");
        dialog.setHeaderText("Rechazo de Solicitud " + sel.getCodigoCatastral());
        dialog.setContentText("Indique el motivo técnico:");

        dialog.showAndWait().ifPresent(motivo -> {
            boolean ok = DatabaseConnectionSupabase.isOnline()
                    ? predioSupabaseDAO.actualizarEstado(sel.getId(), "RECHAZADO", motivo)
                    : predioLocalDAO.actualizarEstado(sel.getId(), "RECHAZADO", motivo);
            if (ok) {
                mostrarAlerta("Solicitud rechazada debidamente.", Alert.AlertType.INFORMATION);
                cargarDatosDashboard();
            }
        });
    }

    private void abrirSoporteDePredio(Predio p) {
        if (p == null) return;
        String url = p.getReciboValidacionUrl();
        if (url != null && !url.isEmpty()) {
            storageService.descargarYAbrirDocumento(url, "recibo_" + p.getCodigoCatastral() + ".pdf");
        } else {
            // Intentar consultar en base de datos si tiene recibo
            DocumentoLegal doc = documentoDAO.obtenerReciboValidacion(p.getId());
            if (doc != null && doc.getStorageUrl() != null) {
                storageService.descargarYAbrirDocumento(doc.getStorageUrl(), doc.getNombreArchivo());
            } else {
                mostrarAlerta("El predio " + p.getCodigoCatastral() + " no tiene recibos o escrituras registradas.", Alert.AlertType.INFORMATION);
            }
        }
    }

    @FXML
    public void cerrarSesion(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Sistema Catastral de Sincelejo");
        } catch (IOException e) {
            System.err.println("Error al cerrar sesión: " + e.getMessage());
        }
    }

    private void mostrarAlerta(String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle("Panel de Administración SIGCAT");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
