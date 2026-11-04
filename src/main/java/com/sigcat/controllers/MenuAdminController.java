package com.sigcat.controllers;

import com.sigcat.dao.AuditLogDAO;
import com.sigcat.dao.DocumentoDAO;
import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.PredioDAOSupabase;
import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.AuditLog;
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
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el Panel de Administración del Sistema SIGCAT.
 * Responsabilidades exclusivas del Administrador:
 * 1. Supervisión de KPIs, métricas globales y tendencias de catastro.
 * 2. Trazabilidad y auditoría forense legal (audit_log).
 * 3. Gestión y directorio de cuentas de usuarios (Propietarios, Funcionarios y Admin).
 * 4. Monitoreo y ejecución de sincronización con Supabase Cloud.
 * 5. Supervisión territorial de la malla cartográfica de Sincelejo.
 *
 * (Nota: Las tareas de dictamen, aprobación y rechazo de predios corresponden
 * exclusivamente al Funcionario Catastral de la Alcaldía).
 */
public class MenuAdminController implements LoginController.RecibeUsuario {

    // ─── SIDEBAR Y HEADER ─────────────────────────────────────────
    @FXML private Circle circleAvatar;
    @FXML private Label lblAdminNombre;
    @FXML private Label lblAdminRol;
    @FXML private Label lblSyncStatus;
    @FXML private TextField txtBuscar;

    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavAuditoria;
    @FXML private Button btnNavUsuarios;
    @FXML private Button btnNavMapa;
    @FXML private Button btnNavSync;

    // ─── CONTENEDORES DE VISTAS DINÁMICAS ─────────────────────────
    @FXML private ScrollPane paneDashboard;
    @FXML private VBox paneAuditoria;
    @FXML private VBox paneUsuarios;

    // ─── TARJETAS KPI (GRADIENTES) ───────────────────────────────
    @FXML private Label lblTotalPredios;
    @FXML private Label lblAreaTotal;
    @FXML private Label lblSolicitudesAuditadas;
    @FXML private Label lblResumenAuditadas;
    @FXML private Label lblTasaAprobacion;
    @FXML private Label lblResumenNorma;

    // ─── TABLA DE INVENTARIO PREDIAL (SOLO INSPECCIÓN) ────────────
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
    @FXML private ProgressBar progressMajagual;
    @FXML private Label lblDonutPercent;

    // ─── TABLA DE AUDITORÍA FORENSE ───────────────────────────────
    @FXML private TableView<AuditLog> tablaAuditoria;
    @FXML private TableColumn<AuditLog, String> colAudFecha;
    @FXML private TableColumn<AuditLog, String> colAudUsuario;
    @FXML private TableColumn<AuditLog, String> colAudAccion;
    @FXML private TableColumn<AuditLog, String> colAudEntidad;
    @FXML private TableColumn<AuditLog, String> colAudAnterior;
    @FXML private TableColumn<AuditLog, String> colAudNuevo;
    @FXML private TableColumn<AuditLog, String> colAudDetalles;

    // ─── TABLA DE GESTIÓN DE USUARIOS ─────────────────────────────
    @FXML private TableView<Usuario> tablaUsuarios;
    @FXML private TableColumn<Usuario, Number> colUsrId;
    @FXML private TableColumn<Usuario, String> colUsrDoc;
    @FXML private TableColumn<Usuario, String> colUsrNombre;
    @FXML private TableColumn<Usuario, String> colUsrRol;
    @FXML private TableColumn<Usuario, String> colUsrEmail;
    @FXML private TableColumn<Usuario, String> colUsrTel;

    private Usuario adminActivo;
    private final PredioDAO predioLocalDAO = new PredioDAO();
    private final PredioDAOSupabase predioSupabaseDAO = new PredioDAOSupabase();
    private final DocumentoDAO documentoDAO = new DocumentoDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final SupabaseStorageService storageService = new SupabaseStorageService();
    private final SyncEngine syncEngine = new SyncEngine();

    private final ObservableList<Predio> listaPrediosObservable = FXCollections.observableArrayList();
    private final ObservableList<AuditLog> listaAuditoriaObservable = FXCollections.observableArrayList();
    private final ObservableList<Usuario> listaUsuariosObservable = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarTablaPredios();
        configurarTablaAuditoria();
        configurarTablaUsuarios();
        configurarGraficoTendencia();

        cargarDatosDashboard();
        verificarEstadoConexion();

        // Monitoreo de Red (Mejora #10)
        com.sigcat.services.NetworkStatusService netService = com.sigcat.services.NetworkStatusService.getInstance();
        netService.iniciarMonitoreo(8);
        if (lblSyncStatus != null) {
            lblSyncStatus.textProperty().bind(netService.estadoTextoProperty());
        }

        // Suscripción Realtime Push (Mejora #1)
        com.sigcat.supabase.SupabaseRealtimeService rtService = com.sigcat.supabase.SupabaseRealtimeService.getInstance();
        rtService.conectar();
        rtService.registrarListenerPredios((event, record) -> Platform.runLater(this::cargarDatosDashboard));
        rtService.registrarListenerAuditoria((event, record) -> Platform.runLater(this::cargarDatosAuditoriaSilencioso));

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
            if (lblAdminRol != null) lblAdminRol.setText("ADMINISTRADOR DEL SISTEMA");
        }
    }

    // ─── CONTROL DE VISTAS (SIDEBAR) ──────────────────────────────

    @FXML
    public void mostrarDashboard(ActionEvent event) {
        activarBotonNav(btnNavDashboard);
        paneDashboard.setVisible(true);
        paneAuditoria.setVisible(false);
        paneUsuarios.setVisible(false);
        cargarDatosDashboard();
    }

    @FXML
    public void mostrarAuditoria(ActionEvent event) {
        activarBotonNav(btnNavAuditoria);
        paneDashboard.setVisible(false);
        paneAuditoria.setVisible(true);
        paneUsuarios.setVisible(false);
        cargarDatosAuditoria();
    }

    @FXML
    public void mostrarUsuarios(ActionEvent event) {
        activarBotonNav(btnNavUsuarios);
        paneDashboard.setVisible(false);
        paneAuditoria.setVisible(false);
        paneUsuarios.setVisible(true);
        cargarDatosUsuarios();
    }

    private void activarBotonNav(Button activo) {
        Button[] btns = {btnNavDashboard, btnNavAuditoria, btnNavUsuarios, btnNavMapa, btnNavSync};
        for (Button b : btns) {
            if (b != null) {
                b.getStyleClass().remove("sidebar-nav-btn-active");
            }
        }
        if (activo != null && !activo.getStyleClass().contains("sidebar-nav-btn-active")) {
            activo.getStyleClass().add("sidebar-nav-btn-active");
        }
    }

    // ─── CONFIGURACIÓN DE TABLAS ──────────────────────────────────

    private void configurarTablaPredios() {
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
            colSoporte.setCellFactory(col -> new TableCell<>() {
                private final Button btnDoc = new Button("📄");
                {
                    btnDoc.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 13px;");
                    btnDoc.setOnAction(e -> {
                        Predio p = getTableView().getItems().get(getIndex());
                        abrirSoportePredio(p);
                    });
                }
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btnDoc);
                }
            });
        }

        tablaSolicitudes.setItems(listaPrediosObservable);
    }

    private void configurarTablaAuditoria() {
        if (tablaAuditoria == null) return;

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (colAudFecha != null) {
            colAudFecha.setCellValueFactory(cellData -> {
                var f = cellData.getValue().getFecha();
                return new SimpleStringProperty(f != null ? f.format(dtf) : "Reciente");
            });
        }
        if (colAudUsuario != null) colAudUsuario.setCellValueFactory(new PropertyValueFactory<>("usuarioNombre"));
        if (colAudAccion != null) colAudAccion.setCellValueFactory(new PropertyValueFactory<>("accion"));
        if (colAudEntidad != null) colAudEntidad.setCellValueFactory(new PropertyValueFactory<>("entidadId"));
        if (colAudAnterior != null) colAudAnterior.setCellValueFactory(new PropertyValueFactory<>("estadoAnterior"));
        if (colAudNuevo != null) colAudNuevo.setCellValueFactory(new PropertyValueFactory<>("estadoNuevo"));
        if (colAudDetalles != null) colAudDetalles.setCellValueFactory(new PropertyValueFactory<>("detalles"));

        tablaAuditoria.setItems(listaAuditoriaObservable);
    }

    private void configurarTablaUsuarios() {
        if (tablaUsuarios == null) return;

        if (colUsrId != null) colUsrId.setCellValueFactory(new PropertyValueFactory<>("id"));
        if (colUsrDoc != null) colUsrDoc.setCellValueFactory(new PropertyValueFactory<>("documento"));
        if (colUsrNombre != null) colUsrNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        if (colUsrRol != null) colUsrRol.setCellValueFactory(new PropertyValueFactory<>("rol"));
        if (colUsrEmail != null) colUsrEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        if (colUsrTel != null) colUsrTel.setCellValueFactory(new PropertyValueFactory<>("telefono"));

        tablaUsuarios.setItems(listaUsuariosObservable);
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

        chartTendencia.getData().add(serieRadicaciones);
        chartTendencia.getData().add(serieAprobados);
    }

    // ─── CARGA DE DATOS ASÍNCRONA ─────────────────────────────────

    @FXML
    public void cargarDatosDashboard() {
        new Thread(() -> {
            List<Predio> lista;
            if (DatabaseConnectionSupabase.isOnline()) {
                lista = predioSupabaseDAO.obtenerTodos();
            } else {
                lista = predioLocalDAO.obtenerTodos();
            }
            List<Usuario> usuarios = usuarioDAO.obtenerTodos();

            Platform.runLater(() -> {
                listaPrediosObservable.setAll(lista);
                actualizarMetricas(lista, usuarios);
            });
        }).start();
    }

    @FXML
    public void cargarDatosAuditoria() {
        new Thread(() -> {
            List<AuditLog> logs = auditLogDAO.listarRecientes(100);
            Platform.runLater(() -> listaAuditoriaObservable.setAll(logs));
        }).start();
    }

    private void cargarDatosAuditoriaSilencioso() {
        if (paneAuditoria != null && paneAuditoria.isVisible()) {
            cargarDatosAuditoria();
        }
    }

    @FXML
    public void cargarDatosUsuarios() {
        new Thread(() -> {
            List<Usuario> usuarios = usuarioDAO.obtenerTodos();
            Platform.runLater(() -> listaUsuariosObservable.setAll(usuarios));
        }).start();
    }

    private void actualizarMetricas(List<Predio> predios, List<Usuario> usuarios) {
        int total = predios.size();
        int aprobados = 0;
        double areaTotal = 0;

        for (Predio p : predios) {
            areaTotal += p.getAreaCalculada();
            if ("APROBADO".equalsIgnoreCase(p.getEstado()) || "PUBLICO".equalsIgnoreCase(p.getEstado())) {
                aprobados++;
            }
        }

        if (lblTotalPredios != null) lblTotalPredios.setText(total + " Predios");
        if (lblAreaTotal != null) lblAreaTotal.setText(String.format("Área total: %,.2f m²", areaTotal));
        if (lblSolicitudesAuditadas != null) lblSolicitudesAuditadas.setText(usuarios.size() + " Usuarios");
        if (lblResumenAuditadas != null) lblResumenAuditadas.setText("Directorio del Sistema");

        double tasa = total > 0 ? ((double) aprobados / total) * 100.0 : 100.0;
        if (lblTasaAprobacion != null) lblTasaAprobacion.setText(String.format("%.0f%%", tasa));
        if (lblDonutPercent != null) lblDonutPercent.setText(String.format("%.0f%%", tasa));

        long countCentro = predios.stream().filter(p -> p.getDireccion() != null && p.getDireccion().toLowerCase().contains("centro")).count();
        long countNorte = predios.stream().filter(p -> p.getDireccion() != null && (p.getDireccion().toLowerCase().contains("cra 28") || p.getDireccion().toLowerCase().contains("norte"))).count();
        long countMajagual = predios.stream().filter(p -> p.getDireccion() != null && (p.getDireccion().toLowerCase().contains("majagual") || p.getDireccion().toLowerCase().contains("palmas"))).count();

        double pctCentro = total > 0 ? Math.min(1.0, 0.40 + (double) countCentro / total) : 0.75;
        double pctNorte = total > 0 ? Math.min(1.0, 0.30 + (double) countNorte / total) : 0.60;
        double pctMajagual = total > 0 ? Math.min(1.0, 0.50 + (double) countMajagual / total) : 0.80;

        if (progressCentro != null) progressCentro.setProgress(pctCentro);
        if (progressNorte != null) progressNorte.setProgress(pctNorte);
        if (progressMajagual != null) progressMajagual.setProgress(pctMajagual);
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

    // ─── ACCIONES DEL ADMINISTRADOR ───────────────────────────────

    @FXML
    public void onSincronizarSupabase(ActionEvent event) {
        if (lblSyncStatus != null) lblSyncStatus.setText("🔄 Sincronizando con Supabase...");
        new Thread(() -> {
            try {
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
            } catch (Exception e) {
                Platform.runLater(() -> {
                    verificarEstadoConexion();
                    mostrarAlerta("Error durante la sincronización: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            }
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
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Supervisión Cartográfica de Sincelejo");
        } catch (IOException e) {
            mostrarAlerta("Error al abrir visor cartográfico: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void onRegistrarFuncionario(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registrar Funcionario / Administrador");
        dialog.setHeaderText("Crear nueva cuenta institucional para la Alcaldía de Sincelejo");

        ButtonType btnGuardar = new ButtonType("💾 Crear Cuenta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField txtCedula = new TextField();
        txtCedula.setPromptText("Ej: 1002");
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre completo del funcionario");
        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Contraseña");
        ComboBox<String> cmbRol = new ComboBox<>();
        cmbRol.getItems().addAll("FUNCIONARIO", "ADMIN");
        cmbRol.setValue("FUNCIONARIO");

        grid.add(new Label("Cédula / Documento:"), 0, 0);
        grid.add(txtCedula, 1, 0);
        grid.add(new Label("Nombre Completo:"), 0, 1);
        grid.add(txtNombre, 1, 1);
        grid.add(new Label("Rol Asignado:"), 0, 2);
        grid.add(cmbRol, 1, 2);
        grid.add(new Label("Contraseña:"), 0, 3);
        grid.add(txtPass, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnGuardar) {
            String doc = txtCedula.getText().trim();
            String nom = txtNombre.getText().trim();
            String pass = txtPass.getText();
            String rol = cmbRol.getValue();

            if (doc.isEmpty() || nom.isEmpty() || pass.isEmpty()) {
                mostrarAlerta("Todos los campos son obligatorios.", Alert.AlertType.WARNING);
                return;
            }

            if (usuarioDAO.existeDocumento(doc)) {
                mostrarAlerta("Ya existe un usuario registrado con el documento " + doc, Alert.AlertType.ERROR);
                return;
            }

            int idNuevo = usuarioDAO.registrarPropietario(doc, nom, pass);
            if (idNuevo > 0) {
                // Actualizar rol si es FUNCIONARIO o ADMIN
                try (var conn = com.sigcat.utils.DatabaseConnection.getConnection();
                     var ps = conn.prepareStatement("UPDATE usuarios SET rol = ? WHERE id_usuario = ?")) {
                    ps.setString(1, rol);
                    ps.setInt(2, idNuevo);
                    ps.executeUpdate();
                } catch (Exception ex) {
                    System.err.println("Error asignando rol: " + ex.getMessage());
                }

                mostrarAlerta("✔ Cuenta de " + rol + " creada exitosamente.", Alert.AlertType.INFORMATION);
                cargarDatosUsuarios();
            } else {
                mostrarAlerta("No se pudo registrar la cuenta.", Alert.AlertType.ERROR);
            }
        }
    }

    private void abrirSoportePredio(Predio p) {
        if (p == null) return;
        List<DocumentoLegal> docs = documentoDAO.listarPorPredio(p.getId());
        if (docs.isEmpty()) {
            mostrarAlerta("El predio " + p.getCodigoCatastral() + " no tiene documentos soporte adjuntos.", Alert.AlertType.INFORMATION);
            return;
        }

        StringBuilder sb = new StringBuilder("Documentos soporte del predio " + p.getCodigoCatastral() + ":\n\n");
        for (DocumentoLegal d : docs) {
            sb.append("• ").append(d.getTipoDocumento()).append(": ").append(d.getNombreArchivo()).append("\n");
            if (d.getHashSha256() != null) {
                sb.append("  Hash SHA-256: ").append(d.getHashSha256().substring(0, Math.min(24, d.getHashSha256().length()))).append("...\n");
            }
            if (d.getStorageUrl() != null && !d.getStorageUrl().isEmpty()) {
                sb.append("  URL Cloud: ").append(d.getStorageUrl()).append("\n");
            }
        }
        mostrarAlerta(sb.toString(), Alert.AlertType.INFORMATION);
    }

    @FXML
    public void cerrarSesion(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Iniciar Sesión");
        } catch (IOException e) {
            mostrarAlerta("Error al cerrar sesión: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void mostrarAlerta(String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle("SIGCAT — Administración");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
