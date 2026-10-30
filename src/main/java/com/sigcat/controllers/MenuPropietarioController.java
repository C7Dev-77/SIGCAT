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
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;

/**
 * Controlador para el Dashboard del Propietario ("Mis Predios").
 * Muestra las solicitudes radicadas con semáforo de colores por estado
 * (Verde = APROBADO, Naranja = PENDIENTE, Rojo = RECHAZADO) y permite navegar al lienzo de dibujo.
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
                !String.valueOf(l1.get(i).getEstado()).equals(String.valueOf(l2.get(i).getEstado()))) {
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
            if (lblEstadoPropietario != null) {
                lblEstadoPropietario.setText("Error al abrir el módulo cartográfico: " + e.getMessage());
            }
        }
    }

    @FXML
    public void verPredioEnMapa(ActionEvent event) {
        Predio seleccionado = tablaMisPredios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            if (lblEstadoPropietario != null) {
                lblEstadoPropietario.setText("Seleccione un predio de la lista para ubicarlo en el mapa.");
                lblEstadoPropietario.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
            }
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
            if (lblEstadoPropietario != null) {
                lblEstadoPropietario.setText("Error al abrir visor cartográfico: " + e.getMessage());
            }
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
