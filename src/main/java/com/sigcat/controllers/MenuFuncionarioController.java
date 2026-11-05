package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.UsuarioDAO;
import com.sigcat.dao.VerticeDAO;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.ResumenMensual;
import com.sigcat.models.Usuario;
import com.sigcat.utils.ViewNavigator;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el Dashboard del Funcionario Catastral.
 * Permite auditar solicitudes pendientes, verificar áreas, agregar dictámenes técnicos,
 * editar datos de predios, eliminar solicitudes y aprobar o rechazar con sincronización en tiempo real.
 */
public class MenuFuncionarioController implements LoginController.RecibeUsuario {

    @FXML private TableView<Predio> tablaSolicitudes;
    @FXML private TableColumn<Predio, String> colCodigo;
    @FXML private TableColumn<Predio, String> colNombre;
    @FXML private TableColumn<Predio, String> colDireccion;
    @FXML private TableColumn<Predio, String> colPropietario;
    @FXML private TableColumn<Predio, Double> colArea;
    @FXML private TableColumn<Predio, String> colEstado;

    @FXML private Label lblFuncionarioNombre;
    @FXML private Label lblFuncionarioRol;
    @FXML private Label lblResumenRechazados;

    @FXML private TextArea txtObservacionesDictamen;
    @FXML private Label lblResumenPendientes;
    @FXML private Label lblResumenAprobados;
    @FXML private Label lblMensajeFeedback;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private HBox boxFiltroEstado;

    // ─── Navegación entre vistas ───
    @FXML private Button btnNavBandeja;
    @FXML private Button btnNavResumen;
    @FXML private VBox panelBandeja;
    @FXML private VBox panelResumen;

    // ─── Resumen mensual ───
    @FXML private ComboBox<String> cmbAnioResumen;
    @FXML private ComboBox<String> cmbDatoResumen;
    @FXML private TableView<ResumenMensual> tablaResumen;
    @FXML private TableColumn<ResumenMensual, String> colResMes;
    @FXML private TableColumn<ResumenMensual, Integer> colResRegistrados;
    @FXML private TableColumn<ResumenMensual, Integer> colResPendientes;
    @FXML private TableColumn<ResumenMensual, Integer> colResAprobados;
    @FXML private TableColumn<ResumenMensual, Integer> colResRechazados;
    @FXML private BarChart<String, Number> graficoResumen;
    @FXML private Label lblTotalesResumen;

    private static final String TODOS_LOS_ANIOS = "Todos los años";
    private static final String DATO_TODOS = "Todos los datos";
    private static final String DATO_REGISTRADOS = "Registrados";
    private static final String DATO_PENDIENTES = "Pendientes";
    private static final String DATO_APROBADOS = "Aprobados";
    private static final String DATO_RECHAZADOS = "Rechazados";

    /** Evita recargar el resumen mientras se rellenan sus ComboBox. */
    private boolean actualizandoFiltrosResumen = false;

    private final PredioDAO predioDAO = new PredioDAO();
    private final VerticeDAO verticeDAO = new VerticeDAO();
    private final com.sigcat.dao.PredioDAOSupabase predioSupabaseDAO = new com.sigcat.dao.PredioDAOSupabase();
    private final com.sigcat.sync.SyncEngine syncEngine = new com.sigcat.sync.SyncEngine();
    private final com.sigcat.services.AuditService auditService = new com.sigcat.services.AuditService();
    private final com.sigcat.services.CertificadoCatastralService certService = new com.sigcat.services.CertificadoCatastralService();
    private final ObservableList<Predio> listaObservable = FXCollections.observableArrayList();
    private Usuario funcionario;
    private Timeline timerAutoRefresco;

    @FXML
    public void initialize() {
        configurarColumnasTabla();

        if (cmbFiltroEstado != null) {
            cmbFiltroEstado.getItems().clear();
            cmbFiltroEstado.getItems().addAll("TODAS LAS SOLICITUDES", "PENDIENTE", "APROBADO", "RECHAZADO");
            cmbFiltroEstado.setValue("TODAS LAS SOLICITUDES");
            cmbFiltroEstado.setOnAction(e -> cargarSolicitudesPorFiltro());
        }

        configurarResumenMensual();

        cargarSolicitudesPorFiltro();
        actualizarContadoresDashboard();
        iniciarAutoRefresco();

        // Suscripción Realtime Push (Mejora #1) y Monitoreo de Red (Mejora #10)
        com.sigcat.supabase.SupabaseRealtimeService.getInstance().conectar();
        com.sigcat.supabase.SupabaseRealtimeService.getInstance().registrarListenerPredios((event, record) -> {
            new Thread(() -> {
                try {
                    syncEngine.sincronizar();
                    Platform.runLater(this::refrescarSilencioso);
                } catch (Exception ignored) {}
            }).start();
        });
        com.sigcat.services.NetworkStatusService.getInstance().iniciarMonitoreo(10);
    }

    private void iniciarAutoRefresco() {
        // Sondeo cada 3.5 segundos con sincronización en la nube Supabase (Multi-PC)
        timerAutoRefresco = new Timeline(new KeyFrame(Duration.seconds(3.5), event -> {
            if (com.sigcat.supabase.DatabaseConnectionSupabase.isOnline()) {
                new Thread(() -> {
                    try {
                        syncEngine.sincronizar();
                        Platform.runLater(this::refrescarSilencioso);
                    } catch (Exception ignored) {}
                }).start();
            } else {
                refrescarSilencioso();
            }
        }));
        timerAutoRefresco.setCycleCount(Animation.INDEFINITE);
        timerAutoRefresco.play();
    }

    public void detenerAutoRefresco() {
        if (timerAutoRefresco != null) {
            timerAutoRefresco.stop();
        }
    }

    private void refrescarSilencioso() {
        String filtro = cmbFiltroEstado != null ? cmbFiltroEstado.getValue() : "TODAS LAS SOLICITUDES";
        List<Predio> nuevos = predioDAO.obtenerPorEstado(filtro);
        if (nuevos.size() != listaObservable.size() || !sonListasEquivalentes(nuevos, listaObservable)) {
            Predio sel = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
            int selId = sel != null ? sel.getId() : -1;

            listaObservable.setAll(nuevos);
            actualizarContadoresDashboard();

            if (selId != -1 && tablaSolicitudes != null) {
                for (Predio p : listaObservable) {
                    if (p.getId() == selId) {
                        tablaSolicitudes.getSelectionModel().select(p);
                        break;
                    }
                }
            }
        }
    }

    private boolean sonListasEquivalentes(List<Predio> l1, List<Predio> l2) {
        if (l1.size() != l2.size()) return false;
        for (int i = 0; i < l1.size(); i++) {
            if (l1.get(i).getId() != l2.get(i).getId() ||
                !String.valueOf(l1.get(i).getEstado()).equals(String.valueOf(l2.get(i).getEstado())) ||
                !String.valueOf(l1.get(i).getNombre()).equals(String.valueOf(l2.get(i).getNombre())) ||
                !String.valueOf(l1.get(i).getDireccion()).equals(String.valueOf(l2.get(i).getDireccion()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setUsuario(Usuario usuario) {
        this.funcionario = usuario;
        if (usuario != null) {
            if (lblFuncionarioNombre != null) lblFuncionarioNombre.setText(usuario.getNombre());
            if (lblFuncionarioRol != null) lblFuncionarioRol.setText("ROL: " + usuario.getRol());
        }
    }

    private void configurarColumnasTabla() {
        if (tablaSolicitudes == null) return;

        if (colCodigo != null) colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoCatastral"));
        if (colNombre != null) colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        if (colDireccion != null) colDireccion.setCellValueFactory(new PropertyValueFactory<>("direccion"));
        
        if (colPropietario != null) {
            colPropietario.setCellValueFactory(cellData -> {
                String nom = cellData.getValue().getNombrePropietario();
                return new SimpleStringProperty(nom != null && !nom.isEmpty() ? nom : "Propietario #" + cellData.getValue().getPropietarioId());
            });
        }

        if (colArea != null) {
            colArea.setCellValueFactory(new PropertyValueFactory<>("area"));
            colArea.setCellFactory(col -> new TableCell<Predio, Double>() {
                @Override
                protected void updateItem(Double area, boolean empty) {
                    super.updateItem(area, empty);
                    if (empty || area == null) {
                        setText(null);
                    } else {
                        setText(String.format("%.2f m²", area));
                    }
                }
            });
        }
        if (colEstado != null) {
            colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
            colEstado.setCellFactory(col -> new TableCell<Predio, String>() {
                @Override
                protected void updateItem(String estado, boolean empty) {
                    super.updateItem(estado, empty);
                    if (empty || estado == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(estado.toUpperCase());
                        if ("APROBADO".equalsIgnoreCase(estado)) {
                            setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                        } else if ("RECHAZADO".equalsIgnoreCase(estado)) {
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                        }
                    }
                }
            });
        }

        tablaSolicitudes.setRowFactory(tv -> {
            TableRow<Predio> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    inspeccionarEnMapa(new ActionEvent(row, null));
                }
            });
            return row;
        });

        tablaSolicitudes.setItems(listaObservable);
    }

    @FXML
    public void cargarSolicitudesPorFiltro() {
        if (com.sigcat.supabase.DatabaseConnectionSupabase.isOnline()) {
            new Thread(() -> {
                try {
                    syncEngine.sincronizar();
                    Platform.runLater(this::refrescarSilencioso);
                } catch (Exception ignored) {}
            }).start();
        }
        String filtro = cmbFiltroEstado != null ? cmbFiltroEstado.getValue() : "TODAS LAS SOLICITUDES";
        List<Predio> resultados = predioDAO.obtenerPorEstado(filtro);
        listaObservable.setAll(resultados);
        actualizarContadoresDashboard();
    }

    @FXML
    public void aprobarSolicitudSeleccionada() {
        procesarDictamen("APROBADO");
    }

    @FXML
    public void rechazarSolicitudSeleccionada() {
        procesarDictamen("RECHAZADO");
    }

    private void procesarDictamen(String nuevoEstado) {
        if (tablaSolicitudes == null) return;
        Predio seleccionado = tablaSolicitudes.getSelectionModel().getSelectedItem();

        if (seleccionado == null) {
            mostrarFeedback("Seleccione un predio de la tabla para emitir dictamen.", "#d97706");
            return;
        }

        // Un dictamen es definitivo: solo se pueden aprobar o rechazar solicitudes PENDIENTES.
        // (Con el filtro "TODAS LAS SOLICITUDES" la tabla también muestra predios ya dictaminados.)
        if (!"PENDIENTE".equalsIgnoreCase(seleccionado.getEstado())) {
            mostrarFeedback("La solicitud " + seleccionado.getCodigoCatastral() + " ya fue dictaminada ("
                    + seleccionado.getEstado().toUpperCase() + "). Solo se pueden decidir solicitudes PENDIENTES.", "#d97706");
            return;
        }

        String obs = txtObservacionesDictamen != null ? txtObservacionesDictamen.getText().trim() : "";
        if ("RECHAZADO".equals(nuevoEstado) && obs.isEmpty()) {
            mostrarFeedback("Para rechazar una solicitud es obligatorio indicar la justificación técnica.", "#dc2626");
            return;
        }

        if (obs.isEmpty()) {
            obs = "Aprobado conforme a validación geométrica y normativa catastral de Sincelejo.";
        }

        // Cargar vértices del predio seleccionado si aún no se han poblado
        if (seleccionado.getVertices() == null || seleccionado.getVertices().isEmpty()) {
            try {
                seleccionado.setVertices(verticeDAO.obtenerPorPredio(seleccionado.getId()));
            } catch (Exception e) {
                System.err.println("Error al cargar vértices para dictamen: " + e.getMessage());
            }
        }

        // 1. VALIDACIÓN TOPOLÓGICA PREVIA A LA APROBACIÓN
        if ("APROBADO".equals(nuevoEstado)) {
            List<Predio> mallaOficial = predioDAO.obtenerPrediosParaValidacionTopologica();
            for (Predio oficial : mallaOficial) {
                if (oficial.getId() == seleccionado.getId()) continue;
                if (seleccionado.getVertices() != null && oficial.getVertices() != null &&
                    SolapamientoValidator.PoligonosSeSolapan(seleccionado.getVertices(), oficial.getVertices())) {
                    mostrarFeedback("❌ ACCIÓN BLOQUEADA: No se puede aprobar porque invade el predio oficial " +
                            oficial.getCodigoCatastral() + " (" + (oficial.getNombre() != null ? oficial.getNombre() : "Lote") + ").", "#dc2626");
                    return;
                }
            }
        }

        String estadoAnterior = seleccionado.getEstado();
        boolean exito = predioDAO.actualizarEstado(seleccionado.getId(), nuevoEstado, obs);
        if (exito) {
            if (txtObservacionesDictamen != null) txtObservacionesDictamen.clear();

            // Replicar dictamen inmediatamente a Supabase Cloud (Multi-PC)
            final String obsDictamen = obs;
            new Thread(() -> {
                try {
                    predioSupabaseDAO.actualizarEstadoPorCodigo(seleccionado.getCodigoCatastral(), nuevoEstado, obsDictamen);
                } catch (Exception ex) {
                    System.err.println("[SUPABASE] Aviso actualizando dictamen en nube: " + ex.getMessage());
                }
            }).start();

            // Auditoría formal de mutación catastral (Mejora #4)
            auditService.auditarCambioEstadoPredio(seleccionado, estadoAnterior, nuevoEstado, funcionario, obs);

            // Si es Aprobado: Generación automática de Certificado Catastral firmado con QR (Mejoras #3 y #9)
            if ("APROBADO".equals(nuevoEstado)) {
                new Thread(() -> {
                    try {
                        UsuarioDAO uDAO = new UsuarioDAO();
                        Usuario prop = uDAO.buscarPorId(seleccionado.getPropietarioId()).orElse(null);
                        certService.generarYGuardarCertificado(seleccionado, funcionario, prop);
                        System.out.println("[CERTIFICADO] ✔ Certificado de Incorporación generado y firmado con éxito para " + seleccionado.getCodigoCatastral());
                    } catch (Exception ex) {
                        System.err.println("[CERTIFICADO] Aviso: No se pudo generar certificado digital: " + ex.getMessage());
                    }
                }).start();
            }

            // 2. RESOLUCIÓN DE CONFLICTOS: Rechazo automático de solicitudes pendientes que queden invadidas
            int autoRechazados = 0;
            if ("APROBADO".equals(nuevoEstado) && seleccionado.getVertices() != null && !seleccionado.getVertices().isEmpty()) {
                List<Predio> pendientes = predioDAO.obtenerPorEstado("PENDIENTE");
                for (Predio pend : pendientes) {
                    if (pend.getId() == seleccionado.getId()) continue;
                    if (pend.getVertices() == null || pend.getVertices().isEmpty()) {
                        try {
                            pend.setVertices(verticeDAO.obtenerPorPredio(pend.getId()));
                        } catch (Exception ignored) {}
                    }
                    if (pend.getVertices() != null && pend.getVertices().size() >= 3) {
                        if (SolapamientoValidator.PoligonosSeSolapan(seleccionado.getVertices(), pend.getVertices())) {
                            String motivo = "Rechazado automáticamente por conflicto catastral: El terreno fue adjudicado e incorporado bajo el código " +
                                    seleccionado.getCodigoCatastral() + " (" + (seleccionado.getNombre() != null ? seleccionado.getNombre() : "Predio") + ").";
                            predioDAO.actualizarEstado(pend.getId(), "RECHAZADO", motivo);
                            final String motFinal = motivo;
                            new Thread(() -> {
                                try {
                                    predioSupabaseDAO.actualizarEstadoPorCodigo(pend.getCodigoCatastral(), "RECHAZADO", motFinal);
                                } catch (Exception ignored) {}
                            }).start();
                            autoRechazados++;
                        }
                    }
                }
            }

            cargarSolicitudesPorFiltro();

            if (autoRechazados > 0) {
                mostrarFeedback("✔ Predio " + seleccionado.getCodigoCatastral() + " APROBADO. Se rechazaron automáticamente " +
                        autoRechazados + " solicitudes pendientes en conflicto con este terreno.", "#16a34a");
            } else {
                mostrarFeedback("✔ Predio " + seleccionado.getCodigoCatastral() + " marcado como " + nuevoEstado, "#16a34a");
            }
        } else {
            mostrarFeedback("Error al actualizar el estado en la base de datos.", "#dc2626");
        }
    }

    @FXML
    public void eliminarPredioSeleccionado(ActionEvent event) {
        Predio seleccionado = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        if (seleccionado == null) {
            mostrarFeedback("Seleccione una solicitud de la tabla para eliminarla.", "#f59e0b");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Eliminación - Funcionario");
        confirm.setHeaderText("¿Eliminar solicitud " + seleccionado.getCodigoCatastral() + "?");
        confirm.setContentText("Esta acción eliminará el predio '" +
                (seleccionado.getNombre() != null ? seleccionado.getNombre() : "Sin nombre") +
                "' del sistema catastral de Sincelejo.\n\n¿Está seguro de continuar?");

        configurarEstiloDialogo(confirm);

        Optional<ButtonType> respuesta = confirm.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == ButtonType.OK) {
            boolean exito = predioDAO.eliminarPredio(seleccionado.getId());
            if (exito) {
                new Thread(() -> {
                    try {
                        predioSupabaseDAO.eliminarPredioPorCodigo(seleccionado.getCodigoCatastral());
                    } catch (Exception ignored) {}
                }).start();
                cargarSolicitudesPorFiltro();
                mostrarFeedback("✔ Predio " + seleccionado.getCodigoCatastral() + " eliminado del sistema.", "#22c55e");
            } else {
                mostrarFeedback("Error al eliminar el predio de la base de datos.", "#dc2626");
            }
        }
    }

    @FXML
    public void editarPredioSeleccionado(ActionEvent event) {
        Predio seleccionado = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        if (seleccionado == null) {
            mostrarFeedback("Seleccione una solicitud de la tabla para editar sus datos.", "#f59e0b");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar Ficha del Predio (Auditoría)");
        dialog.setHeaderText("Editar datos del predio: " + seleccionado.getCodigoCatastral());
        configurarEstiloDialogo(dialog);

        ButtonType btnGuardar = new ButtonType("💾 Guardar Cambios", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new Insets(18));
        form.setPrefWidth(460);

        Label lblInfo = new Label("Código: " + seleccionado.getCodigoCatastral() +
                " | Área: " + String.format("%.2f m²", seleccionado.getArea()) +
                " | Estado: " + seleccionado.getEstado());
        lblInfo.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label lblNombre = new Label("Nombre o Denominación del Predio *:");
        lblNombre.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");
        TextField txtNombre = new TextField(seleccionado.getNombre() != null ? seleccionado.getNombre() : "");
        txtNombre.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-border-color: #334155; -fx-border-radius: 6; -fx-padding: 8;");

        Label lblDir = new Label("Dirección Nomenclatura Urbana *:");
        lblDir.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");
        TextField txtDir = new TextField(seleccionado.getDireccion() != null ? seleccionado.getDireccion() : "");
        txtDir.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-border-color: #334155; -fx-border-radius: 6; -fx-padding: 8;");

        Label lblUso = new Label("Destinación Económica / Uso del Suelo:");
        lblUso.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");
        ComboBox<String> cmbUso = new ComboBox<>();
        cmbUso.getItems().addAll("Residencial", "Comercial", "Industrial", "Institucional", "Mixto");
        cmbUso.setValue(seleccionado.getUsoSuelo() != null ? seleccionado.getUsoSuelo() : "Residencial");
        cmbUso.setMaxWidth(Double.MAX_VALUE);
        cmbUso.setStyle("-fx-background-color: #1e293b; -fx-border-color: #334155; -fx-border-radius: 6;");

        Label lblObs = new Label("Observaciones Técnicas / Dictamen:");
        lblObs.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");
        TextArea txtObs = new TextArea(seleccionado.getObservaciones() != null ? seleccionado.getObservaciones() : "");
        txtObs.setPrefHeight(65);
        txtObs.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: white; -fx-border-color: #334155; -fx-border-radius: 6;");

        form.getChildren().addAll(lblInfo, new Separator(), lblNombre, txtNombre, lblDir, txtDir, lblUso, cmbUso, lblObs, txtObs);
        dialog.getDialogPane().setContent(form);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == btnGuardar) {
            String nuevoNom = txtNombre.getText().trim();
            String nuevaDir = txtDir.getText().trim();
            String nuevoUso = cmbUso.getValue();
            String nuevaObs = txtObs.getText().trim();

            if (nuevoNom.isEmpty() || nuevaDir.isEmpty()) {
                mostrarFeedback("El nombre y la dirección son obligatorios.", "#ef4444");
                return;
            }

            boolean exito = predioDAO.actualizarDatosPredio(seleccionado.getId(), nuevoNom, nuevaDir, nuevoUso, nuevaObs);
            if (exito) {
                cargarSolicitudesPorFiltro();
                mostrarFeedback("✔ Predio " + seleccionado.getCodigoCatastral() + " actualizado por funcionario.", "#22c55e");
            } else {
                mostrarFeedback("Error al actualizar datos en la base de datos.", "#ef4444");
            }
        }
    }

    private void configurarEstiloDialogo(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: #0f172a;");
        try {
            String css = getClass().getResource("/styles/main.css").toExternalForm();
            pane.getStylesheets().add(css);
        } catch (Exception ignored) {}
    }

    private void actualizarContadoresDashboard() {
        if (lblResumenPendientes != null) {
            int pendientes = predioDAO.obtenerPorEstado("PENDIENTE").size();
            lblResumenPendientes.setText(String.valueOf(pendientes));
        }
        if (lblResumenAprobados != null) {
            int aprobados = predioDAO.obtenerPorEstado("APROBADO").size();
            lblResumenAprobados.setText(String.valueOf(aprobados));
        }
        if (lblResumenRechazados != null) {
            int rechazados = predioDAO.obtenerPorEstado("RECHAZADO").size();
            lblResumenRechazados.setText(rechazados + " Rechazados");
        }
    }

    // ═══════════════════════════ RESUMEN MENSUAL ═══════════════════════════

    private void configurarResumenMensual() {
        if (tablaResumen == null) return;

        colResMes.setCellValueFactory(new PropertyValueFactory<>("periodo"));
        colResRegistrados.setCellValueFactory(new PropertyValueFactory<>("registrados"));
        colResPendientes.setCellValueFactory(new PropertyValueFactory<>("pendientes"));
        colResAprobados.setCellValueFactory(new PropertyValueFactory<>("aprobados"));
        colResRechazados.setCellValueFactory(new PropertyValueFactory<>("rechazados"));

        // Mismos colores de estado que la bandeja de solicitudes.
        colorearColumna(colResPendientes, "#f59e0b");
        colorearColumna(colResAprobados, "#22c55e");
        colorearColumna(colResRechazados, "#ef4444");

        actualizandoFiltrosResumen = true;
        cmbDatoResumen.getItems().setAll(DATO_TODOS, DATO_REGISTRADOS, DATO_PENDIENTES, DATO_APROBADOS, DATO_RECHAZADOS);
        cmbDatoResumen.setValue(DATO_TODOS);
        actualizandoFiltrosResumen = false;
    }

    private void colorearColumna(TableColumn<ResumenMensual, Integer> col, String colorHex) {
        col.setCellFactory(c -> new TableCell<ResumenMensual, Integer>() {
            @Override
            protected void updateItem(Integer valor, boolean empty) {
                super.updateItem(valor, empty);
                if (empty || valor == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(valor));
                    setStyle(valor > 0 ? "-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;" : "-fx-text-fill: #94a3b8;");
                }
            }
        });
    }

    @FXML
    public void mostrarBandeja() {
        alternarVista(false);
        cargarSolicitudesPorFiltro();
    }

    @FXML
    public void mostrarResumenMensual() {
        alternarVista(true);
        cargarOpcionesAnioResumen();
        cargarResumenMensual();
    }

    /** Muestra la bandeja o el resumen y marca el botón activo del menú lateral. */
    private void alternarVista(boolean resumen) {
        if (panelBandeja == null || panelResumen == null) return;
        panelBandeja.setVisible(!resumen);
        panelBandeja.setManaged(!resumen);
        panelResumen.setVisible(resumen);
        panelResumen.setManaged(resumen);
        if (boxFiltroEstado != null) {
            boxFiltroEstado.setVisible(!resumen);
            boxFiltroEstado.setManaged(!resumen);
        }
        marcarActivo(btnNavBandeja, !resumen);
        marcarActivo(btnNavResumen, resumen);
    }

    private void marcarActivo(Button boton, boolean activo) {
        if (boton == null) return;
        boton.getStyleClass().remove("sidebar-nav-btn-active");
        if (activo) boton.getStyleClass().add("sidebar-nav-btn-active");
    }

    @FXML
    public void onFiltroResumen() {
        if (!actualizandoFiltrosResumen) {
            cargarResumenMensual();
        }
    }

    @FXML
    public void actualizarResumenMensual() {
        cargarOpcionesAnioResumen();
        cargarResumenMensual();
    }

    /** Llena el filtro de años; por defecto selecciona el año más reciente con movimiento. */
    private void cargarOpcionesAnioResumen() {
        if (cmbAnioResumen == null) return;
        actualizandoFiltrosResumen = true;
        try {
            String anterior = cmbAnioResumen.getValue();
            ObservableList<String> opciones = FXCollections.observableArrayList(TODOS_LOS_ANIOS);
            for (Integer anio : predioDAO.aniosConMovimiento()) {
                opciones.add(String.valueOf(anio));
            }
            String actual = String.valueOf(java.time.Year.now().getValue());
            if (!opciones.contains(actual)) {
                opciones.add(1, actual);
            }
            cmbAnioResumen.setItems(opciones);
            cmbAnioResumen.setValue(anterior != null && opciones.contains(anterior) ? anterior : opciones.get(1));
        } catch (SQLException e) {
            System.err.println("[RESUMEN] Error cargando años: " + e.getMessage());
            cmbAnioResumen.setItems(FXCollections.observableArrayList(TODOS_LOS_ANIOS));
            cmbAnioResumen.setValue(TODOS_LOS_ANIOS);
        } finally {
            actualizandoFiltrosResumen = false;
        }
    }

    private void cargarResumenMensual() {
        if (tablaResumen == null) return;
        String valorAnio = cmbAnioResumen != null ? cmbAnioResumen.getValue() : null;
        Integer anio = (valorAnio == null || TODOS_LOS_ANIOS.equals(valorAnio)) ? null : Integer.valueOf(valorAnio);

        try {
            List<ResumenMensual> filas = predioDAO.resumenMensual(anio);
            tablaResumen.setItems(FXCollections.observableArrayList(filas));
            aplicarFiltroDatoResumen();
            actualizarGraficoResumen(filas);
            mostrarTotalesResumen(filas, anio);
        } catch (SQLException e) {
            System.err.println("[RESUMEN] Error cargando resumen mensual: " + e.getMessage());
            tablaResumen.setItems(FXCollections.observableArrayList());
            if (graficoResumen != null) graficoResumen.getData().clear();
            if (lblTotalesResumen != null) {
                lblTotalesResumen.setText("No se pudo cargar el resumen: " + e.getMessage());
                lblTotalesResumen.setStyle("-fx-text-fill: #dc2626;");
            }
        }
    }

    /** Filtro "Mostrar": deja visible solo la columna elegida (o todas). */
    private void aplicarFiltroDatoResumen() {
        String dato = cmbDatoResumen != null && cmbDatoResumen.getValue() != null ? cmbDatoResumen.getValue() : DATO_TODOS;
        boolean todos = DATO_TODOS.equals(dato);
        colResRegistrados.setVisible(todos || DATO_REGISTRADOS.equals(dato));
        colResPendientes.setVisible(todos || DATO_PENDIENTES.equals(dato));
        colResAprobados.setVisible(todos || DATO_APROBADOS.equals(dato));
        colResRechazados.setVisible(todos || DATO_RECHAZADOS.equals(dato));
    }

    /** Gráfico de barras por mes con las mismas series que muestra la tabla. */
    private void actualizarGraficoResumen(List<ResumenMensual> filas) {
        if (graficoResumen == null) return;
        String dato = cmbDatoResumen != null && cmbDatoResumen.getValue() != null ? cmbDatoResumen.getValue() : DATO_TODOS;
        boolean todos = DATO_TODOS.equals(dato);
        boolean variosAnios = filas.stream().map(ResumenMensual::getAnio).distinct().count() > 1;

        XYChart.Series<String, Number> registrados = new XYChart.Series<>();
        registrados.setName(DATO_REGISTRADOS);
        XYChart.Series<String, Number> pendientes = new XYChart.Series<>();
        pendientes.setName(DATO_PENDIENTES);
        XYChart.Series<String, Number> aprobados = new XYChart.Series<>();
        aprobados.setName(DATO_APROBADOS);
        XYChart.Series<String, Number> rechazados = new XYChart.Series<>();
        rechazados.setName(DATO_RECHAZADOS);

        for (ResumenMensual r : filas) {
            String mes = r.getPeriodo().substring(0, 3) + (variosAnios ? " " + (r.getAnio() % 100) : "");
            registrados.getData().add(new XYChart.Data<>(mes, r.getRegistrados()));
            pendientes.getData().add(new XYChart.Data<>(mes, r.getPendientes()));
            aprobados.getData().add(new XYChart.Data<>(mes, r.getAprobados()));
            rechazados.getData().add(new XYChart.Data<>(mes, r.getRechazados()));
        }

        graficoResumen.getData().clear();
        if (todos || DATO_REGISTRADOS.equals(dato)) graficoResumen.getData().add(registrados);
        if (todos || DATO_PENDIENTES.equals(dato)) graficoResumen.getData().add(pendientes);
        if (todos || DATO_APROBADOS.equals(dato)) graficoResumen.getData().add(aprobados);
        if (todos || DATO_RECHAZADOS.equals(dato)) graficoResumen.getData().add(rechazados);

        // Colores fijos por serie (los nodos de las barras existen después de agregarlas).
        for (XYChart.Series<String, Number> serie : graficoResumen.getData()) {
            String color = switch (serie.getName()) {
                case DATO_PENDIENTES -> "#f59e0b";
                case DATO_APROBADOS -> "#22c55e";
                case DATO_RECHAZADOS -> "#ef4444";
                default -> "#5C6BC0";
            };
            for (XYChart.Data<String, Number> d : serie.getData()) {
                if (d.getNode() != null) d.getNode().setStyle("-fx-bar-fill: " + color + ";");
            }
        }
        Platform.runLater(() -> colorearLeyendaResumen());
    }

    /** La leyenda del BarChart usa colores por defecto; se igualan a los de las barras. */
    private void colorearLeyendaResumen() {
        if (graficoResumen == null) return;
        for (javafx.scene.Node item : graficoResumen.lookupAll(".chart-legend-item")) {
            if (item instanceof Label etiqueta && etiqueta.getGraphic() != null) {
                String color = switch (etiqueta.getText()) {
                    case DATO_PENDIENTES -> "#f59e0b";
                    case DATO_APROBADOS -> "#22c55e";
                    case DATO_RECHAZADOS -> "#ef4444";
                    default -> "#5C6BC0";
                };
                etiqueta.getGraphic().setStyle("-fx-background-color: " + color + ";");
            }
        }
    }

    private void mostrarTotalesResumen(List<ResumenMensual> filas, Integer anio) {
        if (lblTotalesResumen == null) return;
        int reg = 0, pend = 0, apr = 0, rech = 0;
        for (ResumenMensual r : filas) {
            reg += r.getRegistrados();
            pend += r.getPendientes();
            apr += r.getAprobados();
            rech += r.getRechazados();
        }
        String periodo = (anio == null) ? "Total histórico" : "Total " + anio;
        lblTotalesResumen.setText(String.format(
            "%s:  %d registrados  ·  %d pendientes  ·  %d aprobados  ·  %d rechazados",
            periodo, reg, pend, apr, rech));
        lblTotalesResumen.setStyle("");
    }

    @FXML
    public void inspeccionarEnMapa(ActionEvent event) {
        Predio seleccionado = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        try {
            detenerAutoRefresco();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dibujo.fxml"));
            Parent root = loader.load();
            DibujoController controller = loader.getController();
            if (controller != null) {
                controller.activarModoInspeccionFuncionario(seleccionado, funcionario);
            }
            Stage stage = ViewNavigator.obtenerStage(event);
            if (stage == null && tablaSolicitudes != null && tablaSolicitudes.getScene() != null) {
                stage = (Stage) tablaSolicitudes.getScene().getWindow();
            }
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Auditoría e Inspección Cartográfica (Sincelejo)");
        } catch (IOException e) {
            mostrarFeedback("Error al abrir visor cartográfico: " + e.getMessage(), "#dc2626");
        }
    }

    @FXML
    public void verDocumentosSeleccionado() {
        Predio sel = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarFeedback("Seleccione un predio de la tabla para ver sus documentos soporte.", "#f59e0b");
            return;
        }
        com.sigcat.dao.DocumentoDAO docDAO = new com.sigcat.dao.DocumentoDAO();
        List<com.sigcat.models.DocumentoLegal> docs = docDAO.listarPorPredio(sel.getId());
        if (docs.isEmpty()) {
            mostrarFeedback("El predio " + sel.getCodigoCatastral() + " no tiene documentos soporte adjuntos.", "#f59e0b");
            return;
        }
        StringBuilder sb = new StringBuilder("Expediente Documental - Predio " + sel.getCodigoCatastral() + ":\n\n");
        for (var d : docs) {
            sb.append("• ").append(d.getTipoDocumento()).append(": ").append(d.getNombreArchivo()).append("\n");
            if (d.getHashSha256() != null) {
                sb.append("  Hash SHA-256: ").append(d.getHashSha256().substring(0, Math.min(24, d.getHashSha256().length()))).append("...\n");
            }
            if (d.getStorageUrl() != null && !d.getStorageUrl().isEmpty()) {
                sb.append("  URL Cloud: ").append(d.getStorageUrl()).append("\n");
            }
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Expediente Documental Catastral");
        a.setHeaderText("Documentos de soporte legal de " + sel.getNombre());
        a.setContentText(sb.toString());
        configurarEstiloDialogo(a);
        a.showAndWait();
    }

    @FXML
    public void abrirCertificadoSeleccionado() {
        Predio sel = tablaSolicitudes != null ? tablaSolicitudes.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarFeedback("Seleccione un predio para consultar su certificado oficial.", "#f59e0b");
            return;
        }
        if (!"APROBADO".equalsIgnoreCase(sel.getEstado())) {
            mostrarFeedback("Solo los predios APROBADOS disponen de Certificado de Incorporación Catastral.", "#f59e0b");
            return;
        }
        new Thread(() -> {
            try {
                com.sigcat.dao.UsuarioDAO uDAO = new com.sigcat.dao.UsuarioDAO();
                Usuario prop = uDAO.buscarPorId(sel.getPropietarioId()).orElse(null);
                com.sigcat.models.DocumentoLegal doc = certService.generarYGuardarCertificado(sel, funcionario, prop);
                Platform.runLater(() -> {
                    String info = "Archivo: " + doc.getNombreArchivo() + "\n\nHash SHA-256 (Firma Digital):\n" + doc.getHashSha256() +
                            (doc.getStorageUrl() != null && !doc.getStorageUrl().isEmpty() ? "\n\nURL Cloud Supabase:\n" + doc.getStorageUrl() : "");
                    mostrarFeedback("✔ Certificado oficial generado: " + doc.getNombreArchivo(), "#22c55e");
                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setTitle("Certificado Catastral Oficial");
                    a.setHeaderText("Certificado de Incorporación Catastral (DANE 70001)");
                    a.setContentText(info);
                    configurarEstiloDialogo(a);
                    a.showAndWait();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> mostrarFeedback("Aviso al consultar certificado: " + ex.getMessage(), "#dc2626"));
            }
        }).start();
    }

    @FXML
    public void cerrarSesion(ActionEvent event) {
        try {
            detenerAutoRefresco();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Iniciar Sesión");
        } catch (IOException e) {
            mostrarFeedback("Error al cerrar sesión: " + e.getMessage(), "#dc2626");
        }
    }

    private void mostrarFeedback(String mensaje, String colorHex) {
        if (lblMensajeFeedback != null) {
            lblMensajeFeedback.setText(mensaje);
            lblMensajeFeedback.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
        }
    }
}
