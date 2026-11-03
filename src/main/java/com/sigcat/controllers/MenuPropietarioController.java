package com.sigcat.controllers;

import com.sigcat.dao.DocumentoDAO;
import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.supabase.SupabaseStorageService;
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
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el Dashboard del Propietario ("Mis Predios").
 * Integra consulta cartográfica, validación legal de predios mediante subida de soportes a Supabase Storage,
 * visualización de recibos de validación catastral y administración del perfil de usuario.
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
    @FXML private TableColumn<Predio, Void> colRecibo;
    @FXML private TableColumn<Predio, String> colObservaciones;

    private final PredioDAO predioDAO = new PredioDAO();
    private final DocumentoDAO documentoDAO = new DocumentoDAO();
    private final SupabaseStorageService storageService = new SupabaseStorageService();
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
                !String.valueOf(l1.get(i).getDireccion()).equals(String.valueOf(l2.get(i).getDireccion())) ||
                !String.valueOf(l1.get(i).getReciboValidacionUrl()).equals(String.valueOf(l2.get(i).getReciboValidacionUrl()))) {
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

        // Columna de Soporte Legal con Botón "📄 Ver Recibo"
        if (colRecibo != null) {
            colRecibo.setCellFactory(col -> new TableCell<>() {
                private final Button btnVer = new Button("📄 Ver Recibo");
                private final Label lblSin = new Label("⚠️ Sin soporte");
                {
                    btnVer.setStyle("-fx-background-color: #5C6BC0; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
                    lblSin.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                    btnVer.setOnAction(e -> {
                        Predio p = getTableView().getItems().get(getIndex());
                        if (p != null) {
                            abrirReciboDePredio(p);
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                    } else {
                        Predio p = getTableView().getItems().get(getIndex());
                        if (p.getReciboValidacionUrl() != null && !p.getReciboValidacionUrl().isEmpty()) {
                            setGraphic(btnVer);
                        } else {
                            setGraphic(lblSin);
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

    /**
     * Diálogo para que el propietario suba escrituras o recibos de validación catastral a Supabase Storage.
     */
    @FXML
    public void abrirDialogoSubirDocumento(ActionEvent event) {
        Predio seleccionado = tablaMisPredios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarEstado("Seleccione un predio de la tabla para adjuntar su soporte legal o recibo.", "#f59e0b");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Gestión Documental Legal — SIGCAT");
        dialog.setHeaderText("Subir soporte legal para: " + seleccionado.getCodigoCatastral() + " (" + seleccionado.getNombre() + ")");
        configurarEstiloDialogo(dialog);

        ButtonType btnSubir = new ButtonType("☁️ Subir a Supabase Storage", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSubir, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new Insets(18));
        form.setPrefWidth(480);

        Label lblTipo = new Label("Tipo de Documento Legal *:");
        lblTipo.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");
        ComboBox<String> cmbTipo = new ComboBox<>();
        cmbTipo.getItems().addAll("RECIBO_VALIDACION", "ESCRITURA_PUBLICA", "CERTIFICADO_CATASTRO", "FOLIO_MATRICULA", "OTRO");
        cmbTipo.getSelectionModel().selectFirst();
        cmbTipo.setMaxWidth(Double.MAX_VALUE);
        cmbTipo.setStyle("-fx-background-color: #1e293b; -fx-border-color: #334155; -fx-border-radius: 6;");

        Label lblDesc = new Label("Descripción / Notaría / Referencia:");
        lblDesc.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");
        TextField txtDesc = new TextField();
        txtDesc.setPromptText("Ej: Recibo de validación catastral expedido en Sincelejo");
        txtDesc.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-border-color: #334155; -fx-border-radius: 6; -fx-padding: 8;");

        Label lblArchivo = new Label("Archivo Soporte (PDF, JPG, PNG - máx. 15 MB) *:");
        lblArchivo.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: bold;");

        HBox boxFile = new HBox(10);
        boxFile.setAlignment(Pos.CENTER_LEFT);
        Button btnElegir = new Button("📁 Seleccionar Archivo...");
        btnElegir.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        Label lblRuta = new Label("Ningún archivo seleccionado");
        lblRuta.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11.5px;");
        boxFile.getChildren().addAll(btnElegir, lblRuta);

        final File[] archivoSeleccionado = new File[1];
        btnElegir.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Seleccionar Soporte Legal");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documentos Legales (PDF, Imagen)", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
            );
            File f = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) {
                if (f.length() > 15 * 1024 * 1024) {
                    mostrarEstado("El archivo supera el límite de 15 MB.", "#ef4444");
                    return;
                }
                archivoSeleccionado[0] = f;
                lblRuta.setText(f.getName() + " (" + (f.length() / 1024) + " KB)");
                lblRuta.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold;");
            }
        });

        form.getChildren().addAll(lblTipo, cmbTipo, lblDesc, txtDesc, lblArchivo, boxFile);
        dialog.getDialogPane().setContent(form);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == btnSubir) {
            if (archivoSeleccionado[0] == null) {
                mostrarEstado("Debe seleccionar un archivo para subir.", "#ef4444");
                return;
            }

            File archivo = archivoSeleccionado[0];
            String tipoDoc = cmbTipo.getValue();
            String desc = txtDesc.getText().trim();
            mostrarEstado("Subiendo documento a Supabase Storage...", "#38bdf8");

            new Thread(() -> {
                try {
                    DocumentoLegal doc = storageService.subirDocumento(
                        archivo,
                        seleccionado.getId(),
                        propietarioActual != null ? propietarioActual.getDocumento() : null,
                        tipoDoc,
                        desc
                    );

                    Platform.runLater(() -> {
                        if ("RECIBO_VALIDACION".equals(tipoDoc) || seleccionado.getReciboValidacionUrl() == null) {
                            seleccionado.setReciboValidacionUrl(doc.getStorageUrl());
                            predioDAO.actualizarReciboValidacion(seleccionado.getId(), doc.getStorageUrl());
                        }
                        cargarMisPredios();
                        mostrarEstado("✔ Documento '" + archivo.getName() + "' subido exitosamente a Supabase.", "#22c55e");

                        Alert exito = new Alert(Alert.AlertType.INFORMATION);
                        exito.setTitle("Documento Registrado");
                        exito.setHeaderText("¡Soporte subido a Supabase Storage!");
                        exito.setContentText("El documento fue asociado exitosamente al predio " + seleccionado.getCodigoCatastral() + ".\n\n" +
                                "URL pública: " + doc.getStorageUrl());
                        configurarEstiloDialogo(exito);
                        exito.showAndWait();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        mostrarEstado("Error al subir archivo a Supabase: " + ex.getMessage(), "#ef4444");
                    });
                }
            }).start();
        }
    }

    /**
     * Consulta y descarga el recibo de validación del predio seleccionado.
     */
    @FXML
    public void verReciboValidacion(ActionEvent event) {
        Predio seleccionado = tablaMisPredios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarEstado("Seleccione un predio de la tabla para ver su recibo.", "#f59e0b");
            return;
        }
        abrirReciboDePredio(seleccionado);
    }

    private void abrirReciboDePredio(Predio p) {
        String url = p.getReciboValidacionUrl();
        if (url != null && !url.isEmpty()) {
            mostrarEstado("Descargando recibo de validación...", "#22c55e");
            storageService.descargarYAbrirDocumento(url, "Recibo_" + p.getCodigoCatastral() + ".pdf");
        } else {
            if (DatabaseConnectionSupabase.isOnline()) {
                mostrarEstado("Buscando recibo en Supabase...", "#38bdf8");
                new Thread(() -> {
                    DocumentoLegal doc = documentoDAO.obtenerReciboValidacion(p.getId());
                    Platform.runLater(() -> {
                        if (doc != null && doc.getStorageUrl() != null) {
                            p.setReciboValidacionUrl(doc.getStorageUrl());
                            predioDAO.actualizarReciboValidacion(p.getId(), doc.getStorageUrl());
                            storageService.descargarYAbrirDocumento(doc.getStorageUrl(), "Recibo_" + p.getCodigoCatastral() + ".pdf");
                            cargarMisPredios();
                        } else {
                            mostrarEstado("El predio no cuenta con recibo adjunto. Use el botón 'Subir Soporte Legal'.", "#f59e0b");
                        }
                    });
                }).start();
            } else {
                mostrarEstado("El predio no cuenta con recibo adjunto.", "#f59e0b");
            }
        }
    }

    /**
     * Abre la vista integral de Perfil del Propietario y Centro Documental.
     */
    @FXML
    public void abrirMiPerfil(ActionEvent event) {
        if (propietarioActual == null) {
            mostrarEstado("No se encontró sesión activa de propietario.", "#f59e0b");
            return;
        }

        try {
            detenerAutoRefresco();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-propietario-perfil.fxml"));
            Parent root = loader.load();

            MenuPropietarioPerfilController controller = loader.getController();
            if (controller != null) {
                controller.setUsuario(propietarioActual);
            }

            Stage stage = ViewNavigator.obtenerStage(event);
            if (stage == null && tablaMisPredios != null && tablaMisPredios.getScene() != null) {
                stage = (Stage) tablaMisPredios.getScene().getWindow();
            }

            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Perfil de Propietario y Centro Documental");
        } catch (IOException e) {
            mostrarEstado("Error al abrir perfil y centro documental: " + e.getMessage(), "#ef4444");
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
