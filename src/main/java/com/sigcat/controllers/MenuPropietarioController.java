package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.utils.ViewNavigator;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el Dashboard del Propietario ("Mis Predios").
 * Muestra las solicitudes radicadas con semáforo de colores por estado
 * (Verde = APROBADO, Naranja = PENDIENTE, Rojo = RECHAZADO), permite navegar al lienzo de dibujo,
 * inspeccionar en mapa, editar datos y eliminar solicitudes.
 */
public class MenuPropietarioController implements LoginController.RecibeUsuario {

    @FXML private Label lblBienvenidaPropietario;
    @FXML private Label lblTotalPredios;
    @FXML private Label lblTotalAprobados;
    @FXML private Label lblTotalPendientes;
    @FXML private Label lblTotalRechazados;
    @FXML private Label lblEstadoPropietario;

    @FXML private TableView<Predio> tablaMisPredios;
    @FXML private TableColumn<Predio, String> colCodigoCatastral;
    @FXML private TableColumn<Predio, String> colNombrePredio;
    @FXML private TableColumn<Predio, String> colDireccionPredio;
    @FXML private TableColumn<Predio, String> colUsoSuelo;
    @FXML private TableColumn<Predio, Double> colAreaPredio;
    @FXML private TableColumn<Predio, String> colEstadoPredio;
    @FXML private TableColumn<Predio, String> colObservaciones;

    private final PredioDAO predioDAO = new PredioDAO();
    private final ObservableList<Predio> misPrediosList = FXCollections.observableArrayList();
    private Usuario propietarioActual;
    private int propietarioId = 1;
    private Timeline timerAutoRefresco;

    @FXML
    public void initialize() {
        configurarTablaMisPredios();
        cargarMisPredios();
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        timerAutoRefresco = new Timeline(new KeyFrame(Duration.seconds(4.0), event -> cargarMisPrediosSilencioso()));
        timerAutoRefresco.setCycleCount(Animation.INDEFINITE);
        timerAutoRefresco.play();
    }

    public void detenerAutoRefresco() {
        if (timerAutoRefresco != null) {
            timerAutoRefresco.stop();
        }
    }

    private void cargarMisPrediosSilencioso() {
        List<Predio> predios = predioDAO.obtenerPorPropietario(propietarioId);
        if (predios.size() != misPrediosList.size() || !sonListasIguales(predios, misPrediosList)) {
            misPrediosList.setAll(predios);
            actualizarMetricasCards(predios);
        }
    }

    private boolean sonListasIguales(List<Predio> l1, List<Predio> l2) {
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

    public void inicializarSesionPropietario(Usuario usuario) {
        this.propietarioActual = usuario;
        if (usuario != null) {
            this.propietarioId = usuario.getId();
            if (lblBienvenidaPropietario != null) {
                lblBienvenidaPropietario.setText("Propietario: " + usuario.getNombre() + " (Doc: " + usuario.getDocumento() + ") — Sincelejo, Sucre");
            }
            cargarMisPredios();
        }
    }

    @Override
    public void setUsuario(Usuario usuario) {
        inicializarSesionPropietario(usuario);
    }

    public void setPropietarioId(int id) {
        this.propietarioId = id;
        cargarMisPredios();
    }

    private void configurarTablaMisPredios() {
        if (tablaMisPredios == null) return;

        if (colCodigoCatastral != null) colCodigoCatastral.setCellValueFactory(new PropertyValueFactory<>("codigoCatastral"));
        if (colNombrePredio != null) colNombrePredio.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        if (colDireccionPredio != null) colDireccionPredio.setCellValueFactory(new PropertyValueFactory<>("direccion"));
        if (colUsoSuelo != null) colUsoSuelo.setCellValueFactory(new PropertyValueFactory<>("usoSuelo"));

        if (colAreaPredio != null) {
            colAreaPredio.setCellValueFactory(new PropertyValueFactory<>("area"));
            colAreaPredio.setCellFactory(col -> new TableCell<>() {
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

        if (colEstadoPredio != null) {
            colEstadoPredio.setCellValueFactory(new PropertyValueFactory<>("estado"));
            colEstadoPredio.setCellFactory(col -> new TableCell<>() {
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

        if (colObservaciones != null) {
            colObservaciones.setCellValueFactory(cellData -> {
                String obs = cellData.getValue().getObservaciones();
                return new SimpleStringProperty(obs != null && !obs.isEmpty() ? obs : "En espera de revisión técnica");
            });
        }

        // Doble clic en una fila para inspeccionar el predio directamente en el mapa
        tablaMisPredios.setRowFactory(tv -> {
            TableRow<Predio> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    verPredioEnMapa(new ActionEvent(row, null));
                }
            });
            return row;
        });

        tablaMisPredios.setItems(misPrediosList);
    }

    @FXML
    public void cargarMisPredios() {
        List<Predio> predios = predioDAO.obtenerPorPropietario(propietarioId);
        misPrediosList.setAll(predios);
        actualizarMetricasCards(predios);
    }

    private void actualizarMetricasCards(List<Predio> predios) {
        long aprobados = predios.stream().filter(p -> "APROBADO".equalsIgnoreCase(p.getEstado())).count();
        long pendientes = predios.stream().filter(p -> "PENDIENTE".equalsIgnoreCase(p.getEstado())).count();
        long rechazados = predios.stream().filter(p -> "RECHAZADO".equalsIgnoreCase(p.getEstado())).count();

        if (lblTotalPredios != null) lblTotalPredios.setText(String.valueOf(predios.size()));
        if (lblTotalAprobados != null) lblTotalAprobados.setText(String.valueOf(aprobados));
        if (lblTotalPendientes != null) lblTotalPendientes.setText(String.valueOf(pendientes));
        if (lblTotalRechazados != null) lblTotalRechazados.setText(String.valueOf(rechazados));
    }

    @FXML
    public void irADibujarNuevoPredio(ActionEvent event) {
        try {
            detenerAutoRefresco();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dibujo.fxml"));
            Parent root = loader.load();
            DibujoController dibujoController = loader.getController();
            if (dibujoController != null) {
                dibujoController.setPropietarioActualId(propietarioId);
                dibujoController.setUsuarioActual(propietarioActual);
            }

            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Levantamiento Georreferenciado en Sincelejo");
        } catch (IOException e) {
            mostrarEstado("Error al abrir el módulo cartográfico: " + e.getMessage(), "#ef4444");
        }
    }

    @FXML
    public void verPredioEnMapa(ActionEvent event) {
        Predio seleccionado = tablaMisPredios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarEstado("Seleccione un predio de la lista para ubicarlo en el mapa.", "#f59e0b");
            return;
        }

        try {
            detenerAutoRefresco();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dibujo.fxml"));
            Parent root = loader.load();
            DibujoController controller = loader.getController();
            if (controller != null) {
                controller.activarModoInspeccionPropietario(seleccionado, propietarioActual);
            }

            Stage stage = ViewNavigator.obtenerStage(event);
            if (stage == null && tablaMisPredios != null && tablaMisPredios.getScene() != null) {
                stage = (Stage) tablaMisPredios.getScene().getWindow();
            }
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Predio Georreferenciado (" + seleccionado.getCodigoCatastral() + ")");
        } catch (IOException e) {
            mostrarEstado("Error al abrir visor cartográfico: " + e.getMessage(), "#ef4444");
        }
    }

    @FXML
    public void eliminarPredioSeleccionado(ActionEvent event) {
        Predio seleccionado = tablaMisPredios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarEstado("Seleccione un predio de la tabla para eliminarlo.", "#f59e0b");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Eliminación de Predio");
        confirm.setHeaderText("¿Eliminar la solicitud del predio " + seleccionado.getCodigoCatastral() + "?");
        confirm.setContentText("Esta acción eliminará permanentemente el predio '" +
                (seleccionado.getNombre() != null ? seleccionado.getNombre() : "Sin nombre") +
                "' y todos sus linderos asociados.\n\n¿Desea continuar?");

        configurarEstiloDialogo(confirm);

        Optional<ButtonType> respuesta = confirm.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == ButtonType.OK) {
            boolean exito = predioDAO.eliminarPredio(seleccionado.getId());
            if (exito) {
                cargarMisPredios();
                mostrarEstado("✔ Predio " + seleccionado.getCodigoCatastral() + " eliminado exitosamente.", "#22c55e");
            } else {
                mostrarEstado("Error al eliminar el predio de la base de datos.", "#ef4444");
            }
        }
    }

    @FXML
    public void editarPredioSeleccionado(ActionEvent event) {
        Predio seleccionado = tablaMisPredios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarEstado("Seleccione un predio de la tabla para editar sus datos.", "#f59e0b");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar Ficha del Predio");
        dialog.setHeaderText("Modificar datos del predio: " + seleccionado.getCodigoCatastral());
        configurarEstiloDialogo(dialog);

        ButtonType btnGuardar = new ButtonType("💾 Guardar Cambios", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new Insets(18));
        form.setPrefWidth(440);

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

        form.getChildren().addAll(lblInfo, new Separator(), lblNombre, txtNombre, lblDir, txtDir, lblUso, cmbUso);
        dialog.getDialogPane().setContent(form);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == btnGuardar) {
            String nuevoNom = txtNombre.getText().trim();
            String nuevaDir = txtDir.getText().trim();
            String nuevoUso = cmbUso.getValue();

            if (nuevoNom.isEmpty() || nuevaDir.isEmpty()) {
                mostrarEstado("El nombre y la dirección son obligatorios.", "#ef4444");
                return;
            }

            boolean exito = predioDAO.actualizarDatosPredio(seleccionado.getId(), nuevoNom, nuevaDir, nuevoUso, seleccionado.getObservaciones());
            if (exito) {
                cargarMisPredios();
                mostrarEstado("✔ Predio " + seleccionado.getCodigoCatastral() + " actualizado correctamente.", "#22c55e");
            } else {
                mostrarEstado("Error al actualizar los datos en la base de datos.", "#ef4444");
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

    private void mostrarEstado(String texto, String colorHex) {
        if (lblEstadoPropietario != null) {
            lblEstadoPropietario.setText(texto);
            lblEstadoPropietario.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
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
            e.printStackTrace();
        }
    }
}
