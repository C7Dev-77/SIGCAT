package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.utils.ViewNavigator;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;

/**
 * Controlador para el Dashboard del Funcionario Catastral.
 * Permite auditar solicitudes pendientes, verificar áreas, agregar dictámenes técnicos
 * y aprobar o rechazar la incorporación del predio al censo catastral con sincronización en tiempo real.
 */
public class MenuFuncionarioController implements LoginController.RecibeUsuario {

    @FXML private TableView<Predio> tablaSolicitudes;
    @FXML private TableColumn<Predio, String> colCodigo;
    @FXML private TableColumn<Predio, String> colNombre;
    @FXML private TableColumn<Predio, String> colDireccion;
    @FXML private TableColumn<Predio, Double> colArea;
    @FXML private TableColumn<Predio, String> colEstado;

    @FXML private TextArea txtObservacionesDictamen;
    @FXML private Label lblResumenPendientes;
    @FXML private Label lblResumenAprobados;
    @FXML private Label lblMensajeFeedback;
    @FXML private ComboBox<String> cmbFiltroEstado;

    private final PredioDAO predioDAO = new PredioDAO();
    private final ObservableList<Predio> listaObservable = FXCollections.observableArrayList();
    private Usuario funcionario;
    private Timeline timerAutoRefresco;

    @FXML
    public void initialize() {
        configurarColumnasTabla();

        if (cmbFiltroEstado != null) {
            cmbFiltroEstado.getItems().addAll("PENDIENTE", "APROBADO", "RECHAZADO");
            cmbFiltroEstado.setValue("PENDIENTE");
            cmbFiltroEstado.setOnAction(e -> cargarSolicitudesPorFiltro());
        }

        cargarSolicitudesPorFiltro();
        actualizarContadoresDashboard();
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        // Sondeo cada 3.5 segundos para sincronizar en vivo con propietarios en red
        timerAutoRefresco = new Timeline(new KeyFrame(Duration.seconds(3.5), event -> refrescarSilencioso()));
        timerAutoRefresco.setCycleCount(Animation.INDEFINITE);
        timerAutoRefresco.play();
    }

    public void detenerAutoRefresco() {
        if (timerAutoRefresco != null) {
            timerAutoRefresco.stop();
        }
    }

    private void refrescarSilencioso() {
        String filtro = cmbFiltroEstado != null ? cmbFiltroEstado.getValue() : "PENDIENTE";
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
            if (lblMensajeFeedback != null) {
                lblMensajeFeedback.setText("Sincronización en red: bandeja actualizada en vivo.");
                lblMensajeFeedback.setStyle("-fx-text-fill: #3b82f6; -fx-font-weight: bold;");
            }
        }
    }

    private boolean sonListasEquivalentes(List<Predio> l1, List<Predio> l2) {
        if (l1.size() != l2.size()) return false;
        for (int i = 0; i < l1.size(); i++) {
            if (l1.get(i).getId() != l2.get(i).getId() ||
                !String.valueOf(l1.get(i).getEstado()).equals(String.valueOf(l2.get(i).getEstado()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setUsuario(Usuario usuario) {
        this.funcionario = usuario;
    }

    private void configurarColumnasTabla() {
        if (tablaSolicitudes == null) return;

        if (colCodigo != null) colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoCatastral"));
        if (colNombre != null) colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        if (colDireccion != null) colDireccion.setCellValueFactory(new PropertyValueFactory<>("direccion"));
        if (colArea != null) {
            colArea.setCellValueFactory(new PropertyValueFactory<>("area"));
            colArea.setCellFactory(col -> new TableCell<>() {
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
            colEstado.setCellFactory(col -> new TableCell<>() {
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
        String filtro = cmbFiltroEstado != null ? cmbFiltroEstado.getValue() : "PENDIENTE";
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

        String obs = txtObservacionesDictamen != null ? txtObservacionesDictamen.getText().trim() : "";
        if ("RECHAZADO".equals(nuevoEstado) && obs.isEmpty()) {
            mostrarFeedback("Para rechazar una solicitud es obligatorio indicar la justificación técnica.", "#dc2626");
            return;
        }

        if (obs.isEmpty()) {
            obs = "Aprobado conforme a validación geométrica y normativa catastral.";
        }

        boolean exito = predioDAO.actualizarEstado(seleccionado.getId(), nuevoEstado, obs);
        if (exito) {
            if (txtObservacionesDictamen != null) txtObservacionesDictamen.clear();
            cargarSolicitudesPorFiltro();
            mostrarFeedback("Predio " + seleccionado.getCodigoCatastral() + " marcado como " + nuevoEstado, "#16a34a");
        } else {
            mostrarFeedback("Error al actualizar el estado en la base de datos.", "#dc2626");
        }
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
