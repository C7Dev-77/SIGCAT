package com.sigcat.controllers;

import com.sigcat.dao.DocumentoDAO;
import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.supabase.SupabaseStorageService;
import com.sigcat.utils.ViewNavigator;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para la Vista Integral de Perfil de Propietario y Centro de Gestión Documental.
 * Permite actualizar datos personales, subir avatares a Supabase Storage, administrar contraseñas
 * y gestionar expedientes de documentos legales (escrituras y recibos) por predio.
 */
public class MenuPropietarioPerfilController implements LoginController.RecibeUsuario {

    @FXML private Label lblSubtituloNav;
    @FXML private ImageView imgAvatar;
    @FXML private Label lblAvatarLetra;
    @FXML private Label lblNombreDisplay;
    @FXML private TextField txtDocumento;
    @FXML private TextField txtNombre;
    @FXML private TextField txtEmail;
    @FXML private TextField txtTelefono;
    @FXML private PasswordField txtPasswordNueva;
    @FXML private PasswordField txtPasswordConfirmar;
    @FXML private Label lblEstadoPerfil;

    @FXML private ComboBox<Predio> cmbMisPredios;
    @FXML private Label lblPredioCodigo;
    @FXML private Label lblPredioEstado;
    @FXML private Label lblPredioNombre;
    @FXML private Label lblPredioDireccion;
    @FXML private Label lblPredioArea;

    @FXML private TableView<DocumentoLegal> tablaDocumentos;
    @FXML private TableColumn<DocumentoLegal, String> colTipoDoc;
    @FXML private TableColumn<DocumentoLegal, String> colNombreArchivo;
    @FXML private TableColumn<DocumentoLegal, String> colDescripcion;
    @FXML private TableColumn<DocumentoLegal, String> colFechaSubida;
    @FXML private TableColumn<DocumentoLegal, String> colEstadoDoc;
    @FXML private TableColumn<DocumentoLegal, Void> colAcciones;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final PredioDAO predioDAO = new PredioDAO();
    private final DocumentoDAO documentoDAO = new DocumentoDAO();
    private final SupabaseStorageService storageService = new SupabaseStorageService();

    private Usuario usuarioActual;
    private final ObservableList<Predio> misPrediosList = FXCollections.observableArrayList();
    private final ObservableList<DocumentoLegal> documentosList = FXCollections.observableArrayList();
    private Predio predioActivo;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        configurarComboPredios();
        configurarTablaDocumentos();
    }

    @Override
    public void setUsuario(Usuario usuario) {
        this.usuarioActual = usuario;
        if (usuario != null) {
            cargarDatosPerfil();
            cargarPrediosPropietario();
        }
    }

    private void cargarDatosPerfil() {
        if (usuarioActual == null) return;

        if (txtDocumento != null) txtDocumento.setText(usuarioActual.getDocumento());
        if (txtNombre != null) txtNombre.setText(usuarioActual.getNombre());
        if (lblNombreDisplay != null) lblNombreDisplay.setText(usuarioActual.getNombre());
        if (txtEmail != null) txtEmail.setText(usuarioActual.getEmail() != null ? usuarioActual.getEmail() : "");
        if (txtTelefono != null) txtTelefono.setText(usuarioActual.getTelefono() != null ? usuarioActual.getTelefono() : "");

        if (lblSubtituloNav != null) {
            lblSubtituloNav.setText("Propietario: " + usuarioActual.getNombre() + " (Doc: " + usuarioActual.getDocumento() + ") — Sincelejo, Sucre");
        }

        cargarAvatarVisual(usuarioActual.getAvatarUrl());
    }

    private void cargarAvatarVisual(String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                Image img = new Image(avatarUrl, 76, 76, true, true, true);
                img.errorProperty().addListener((obs, oldV, err) -> {
                    if (err) mostrarAvatarLetra();
                });
                if (imgAvatar != null) {
                    imgAvatar.setImage(img);
                    Circle clip = new Circle(38, 38, 38);
                    imgAvatar.setClip(clip);
                    if (lblAvatarLetra != null) lblAvatarLetra.setVisible(false);
                }
            } catch (Exception e) {
                mostrarAvatarLetra();
            }
        } else {
            mostrarAvatarLetra();
        }
    }

    private void mostrarAvatarLetra() {
        if (lblAvatarLetra != null) {
            lblAvatarLetra.setVisible(true);
            String letra = (usuarioActual != null && usuarioActual.getNombre() != null && !usuarioActual.getNombre().isEmpty())
                    ? usuarioActual.getNombre().substring(0, 1).toUpperCase()
                    : "👤";
            lblAvatarLetra.setText(letra);
        }
        if (imgAvatar != null) imgAvatar.setImage(null);
    }

    private void cargarPrediosPropietario() {
        if (usuarioActual == null) return;
        List<Predio> predios = predioDAO.obtenerPorPropietario(usuarioActual.getId());
        misPrediosList.setAll(predios);

        if (cmbMisPredios != null) {
            cmbMisPredios.setItems(misPrediosList);
            if (!misPrediosList.isEmpty()) {
                cmbMisPredios.getSelectionModel().selectFirst();
                seleccionarPredio(misPrediosList.get(0));
            } else {
                mostrarPredioVacio();
            }
        }
    }

    private void configurarComboPredios() {
        if (cmbMisPredios == null) return;

        cmbMisPredios.setConverter(new StringConverter<>() {
            @Override
            public String toString(Predio p) {
                if (p == null) return "";
                return p.getCodigoCatastral() + " — " + (p.getNombre() != null ? p.getNombre() : "Sin nombre");
            }

            @Override
            public Predio fromString(String string) {
                return null;
            }
        });

        cmbMisPredios.getSelectionModel().selectedItemProperty().addListener((obs, oldP, newP) -> {
            if (newP != null) {
                seleccionarPredio(newP);
            }
        });
    }

    private void seleccionarPredio(Predio p) {
        this.predioActivo = p;
        if (p == null) return;

        if (lblPredioCodigo != null) lblPredioCodigo.setText(p.getCodigoCatastral());
        if (lblPredioNombre != null) lblPredioNombre.setText(p.getNombre() != null ? p.getNombre() : "Sin nombre");
        if (lblPredioDireccion != null) lblPredioDireccion.setText(p.getDireccion() != null ? p.getDireccion() : "Sincelejo, Sucre");
        if (lblPredioArea != null) lblPredioArea.setText(String.format("%.2f m²", p.getArea()));

        if (lblPredioEstado != null) {
            String est = p.getEstado() != null ? p.getEstado().toUpperCase() : "PENDIENTE";
            lblPredioEstado.setText(est);
            if ("APROBADO".equals(est)) {
                lblPredioEstado.setStyle("-fx-background-color: rgba(34, 197, 94, 0.25); -fx-text-fill: #4ade80; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6;");
            } else if ("RECHAZADO".equals(est)) {
                lblPredioEstado.setStyle("-fx-background-color: rgba(239, 68, 68, 0.25); -fx-text-fill: #f87171; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6;");
            } else {
                lblPredioEstado.setStyle("-fx-background-color: rgba(245, 158, 11, 0.25); -fx-text-fill: #fbbf24; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6;");
            }
        }

        cargarDocumentosDePredio(p.getId());
    }

    private void mostrarPredioVacio() {
        if (lblPredioCodigo != null) lblPredioCodigo.setText("SIN PREDIOS");
        if (lblPredioNombre != null) lblPredioNombre.setText("No ha radicado predios aún");
        if (lblPredioDireccion != null) lblPredioDireccion.setText("Use el botón 'Radicar / Dibujar' para registrar su primer terreno.");
        if (lblPredioArea != null) lblPredioArea.setText("0.00 m²");
        documentosList.clear();
    }

    private void cargarDocumentosDePredio(int predioId) {
        new Thread(() -> {
            List<DocumentoLegal> docs = documentoDAO.listarPorPredio(predioId);
            Platform.runLater(() -> documentosList.setAll(docs));
        }).start();
    }

    private void configurarTablaDocumentos() {
        if (tablaDocumentos == null) return;

        if (colTipoDoc != null) {
            colTipoDoc.setCellValueFactory(new PropertyValueFactory<>("tipoDocumento"));
            colTipoDoc.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String tipo, boolean empty) {
                    super.updateItem(tipo, empty);
                    if (empty || tipo == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(tipo.replace("_", " "));
                        if ("RECIBO_VALIDACION".equals(tipo)) {
                            setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold;");
                        } else if ("ESCRITURA_PUBLICA".equals(tipo)) {
                            setStyle("-fx-text-fill: #a78bfa; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #cbd5e1;");
                        }
                    }
                }
            });
        }

        if (colNombreArchivo != null) colNombreArchivo.setCellValueFactory(new PropertyValueFactory<>("nombreArchivo"));
        if (colDescripcion != null) colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));

        if (colFechaSubida != null) {
            colFechaSubida.setCellValueFactory(cellData -> {
                if (cellData.getValue().getSubidoEn() != null) {
                    return new SimpleStringProperty(cellData.getValue().getSubidoEn().format(FORMATO_FECHA));
                }
                return new SimpleStringProperty("Reciente");
            });
        }

        if (colEstadoDoc != null) {
            colEstadoDoc.setCellValueFactory(new PropertyValueFactory<>("estado"));
            colEstadoDoc.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String estado, boolean empty) {
                    super.updateItem(estado, empty);
                    if (empty || estado == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(estado.replace("_", " "));
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

        if (colAcciones != null) {
            colAcciones.setCellFactory(col -> new TableCell<>() {
                private final Button btnAbrir = new Button("👁️ Ver");
                private final Button btnBorrar = new Button("🗑️");
                private final HBox box = new HBox(6, btnAbrir, btnBorrar);
                {
                    box.setAlignment(Pos.CENTER);
                    btnAbrir.setStyle("-fx-background-color: #5C6BC0; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
                    btnBorrar.setStyle("-fx-background-color: #334155; -fx-text-fill: #f87171; -fx-font-size: 11px; -fx-padding: 3 6 3 6; -fx-background-radius: 4; -fx-cursor: hand;");

                    btnAbrir.setOnAction(e -> {
                        DocumentoLegal doc = getTableView().getItems().get(getIndex());
                        if (doc != null && doc.getStorageUrl() != null) {
                            storageService.descargarYAbrirDocumento(doc.getStorageUrl(), doc.getNombreArchivo());
                        }
                    });

                    btnBorrar.setOnAction(e -> {
                        DocumentoLegal doc = getTableView().getItems().get(getIndex());
                        if (doc != null) {
                            confirmarYEliminarDocumento(doc);
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                    } else {
                        setGraphic(box);
                    }
                }
            });
        }

        tablaDocumentos.setItems(documentosList);
    }

    private void confirmarYEliminarDocumento(DocumentoLegal doc) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar Documento");
        confirm.setHeaderText("¿Eliminar soporte legal?");
        confirm.setContentText("¿Está seguro de eliminar el archivo '" + doc.getNombreArchivo() + "'?\nEsta acción no se puede deshacer.");
        configurarEstiloDialogo(confirm);

        Optional<ButtonType> resp = confirm.showAndWait();
        if (resp.isPresent() && resp.get() == ButtonType.OK) {
            new Thread(() -> {
                boolean ok = documentoDAO.eliminar(doc.getId());
                Platform.runLater(() -> {
                    if (ok) {
                        documentosList.remove(doc);
                        if (predioActivo != null && doc.getStorageUrl() != null && doc.getStorageUrl().equals(predioActivo.getReciboValidacionUrl())) {
                            predioActivo.setReciboValidacionUrl(null);
                            predioDAO.actualizarReciboValidacion(predioActivo.getId(), null);
                        }
                    }
                });
            }).start();
        }
    }

    @FXML
    public void cambiarAvatar(ActionEvent event) {
        if (usuarioActual == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar Foto de Perfil");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes (JPG, PNG)", "*.jpg", "*.jpeg", "*.png")
        );
        File archivo = fc.showOpenDialog(txtNombre != null ? txtNombre.getScene().getWindow() : null);
        if (archivo != null) {
            com.sigcat.utils.FileValidator.ResultadoValidacion val = com.sigcat.utils.FileValidator.validarAvatar(archivo);
            if (!val.valido()) {
                mostrarEstadoPerfil(val.mensaje(), "#ef4444");
                return;
            }

            mostrarEstadoPerfil("Subiendo avatar a Supabase Storage...", "#38bdf8");
            new Thread(() -> {
                try {
                    String publicUrl = storageService.subirAvatar(archivo, usuarioActual.getDocumento());
                    if (publicUrl != null) {
                        usuarioActual.setAvatarUrl(publicUrl);
                        usuarioDAO.actualizarPerfilCompleto(usuarioActual.getId(), usuarioActual.getNombre(), usuarioActual.getEmail(), usuarioActual.getTelefono(), publicUrl, null);
                        Platform.runLater(() -> {
                            cargarAvatarVisual(publicUrl);
                            mostrarEstadoPerfil("✔ Avatar actualizado correctamente.", "#22c55e");
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> mostrarEstadoPerfil("Error subiendo avatar: " + e.getMessage(), "#ef4444"));
                }
            }).start();
        }
    }

    @FXML
    public void guardarPerfil(ActionEvent event) {
        if (usuarioActual == null) return;

        String nom = txtNombre != null ? txtNombre.getText().trim() : "";
        String mail = txtEmail != null ? txtEmail.getText().trim() : "";
        String tel = txtTelefono != null ? txtTelefono.getText().trim() : "";
        String p1 = txtPasswordNueva != null ? txtPasswordNueva.getText().trim() : "";
        String p2 = txtPasswordConfirmar != null ? txtPasswordConfirmar.getText().trim() : "";

        if (nom.isEmpty()) {
            mostrarEstadoPerfil("El nombre completo es obligatorio.", "#ef4444");
            return;
        }

        if (!p1.isEmpty()) {
            if (p1.length() < 4) {
                mostrarEstadoPerfil("La contraseña debe tener al menos 4 caracteres.", "#ef4444");
                return;
            }
            if (!p1.equals(p2)) {
                mostrarEstadoPerfil("Las contraseñas no coinciden.", "#ef4444");
                return;
            }
        }

        boolean ok = usuarioDAO.actualizarPerfilCompleto(usuarioActual.getId(), nom, mail, tel, usuarioActual.getAvatarUrl(), p1.isEmpty() ? null : p1);
        if (ok) {
            usuarioActual.setNombre(nom);
            usuarioActual.setEmail(mail);
            usuarioActual.setTelefono(tel);
            if (lblNombreDisplay != null) lblNombreDisplay.setText(nom);
            if (lblSubtituloNav != null) {
                lblSubtituloNav.setText("Propietario: " + nom + " (Doc: " + usuarioActual.getDocumento() + ") — Sincelejo, Sucre");
            }
            if (txtPasswordNueva != null) txtPasswordNueva.clear();
            if (txtPasswordConfirmar != null) txtPasswordConfirmar.clear();
            mostrarEstadoPerfil("✔ Perfil actualizado exitosamente.", "#22c55e");
        } else {
            mostrarEstadoPerfil("Error al guardar cambios en base de datos.", "#ef4444");
        }
    }

    @FXML
    public void subirDocumentoNuevo(ActionEvent event) {
        if (predioActivo == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Seleccione primero un predio para adjuntar soportes.");
            configurarEstiloDialogo(alert);
            alert.showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Adjuntar Soporte Legal — SIGCAT");
        dialog.setHeaderText("Predio: " + predioActivo.getCodigoCatastral() + " (" + predioActivo.getNombre() + ")");
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
        txtDesc.setPromptText("Ej: Recibo oficial de validación expedido por catastro Sincelejo");
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
                com.sigcat.utils.FileValidator.ResultadoValidacion val = com.sigcat.utils.FileValidator.validarDocumento(f);
                if (!val.valido()) {
                    Alert al = new Alert(Alert.AlertType.WARNING, val.mensaje());
                    configurarEstiloDialogo(al);
                    al.showAndWait();
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
                Alert err = new Alert(Alert.AlertType.WARNING, "Debe seleccionar un archivo para subir.");
                configurarEstiloDialogo(err);
                err.showAndWait();
                return;
            }

            File archivo = archivoSeleccionado[0];
            String tipoDoc = cmbTipo.getValue();
            String desc = txtDesc.getText().trim();

            new Thread(() -> {
                try {
                    DocumentoLegal doc = storageService.subirDocumento(
                            archivo,
                            predioActivo.getId(),
                            usuarioActual != null ? usuarioActual.getDocumento() : null,
                            tipoDoc,
                            desc
                    );

                    // Auditoría del documento (Mejora #4)
                    new com.sigcat.services.AuditService().auditarDocumentoSubido(doc, usuarioActual);

                    Platform.runLater(() -> {
                        if ("RECIBO_VALIDACION".equals(tipoDoc) || predioActivo.getReciboValidacionUrl() == null) {
                            predioActivo.setReciboValidacionUrl(doc.getStorageUrl());
                            predioDAO.actualizarReciboValidacion(predioActivo.getId(), doc.getStorageUrl());
                        }
                        documentosList.add(0, doc);

                        Alert exito = new Alert(Alert.AlertType.INFORMATION);
                        exito.setTitle("Documento Registrado");
                        exito.setHeaderText("¡Soporte subido a Supabase Storage!");
                        exito.setContentText("El documento '" + archivo.getName() + "' fue vinculado exitosamente al predio " + predioActivo.getCodigoCatastral() + ".");
                        configurarEstiloDialogo(exito);
                        exito.showAndWait();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        Alert err = new Alert(Alert.AlertType.ERROR, "Error al subir archivo a Supabase: " + ex.getMessage());
                        configurarEstiloDialogo(err);
                        err.showAndWait();
                    });
                }
            }).start();
        }
    }

    @FXML
    public void recargarDocumentos(ActionEvent event) {
        if (predioActivo != null) {
            cargarDocumentosDePredio(predioActivo.getId());
        }
    }

    @FXML
    public void volverAlMenuPropietario(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-propietario.fxml"));
            Parent root = loader.load();
            MenuPropietarioController controller = loader.getController();
            if (controller != null && usuarioActual != null) {
                controller.inicializarSesionPropietario(usuarioActual);
            }
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Portal del Propietario");
        } catch (IOException e) {
            mostrarEstadoPerfil("Error al volver a mis predios: " + e.getMessage(), "#ef4444");
        }
    }

    @FXML
    public void irAlMapa(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dibujo.fxml"));
            Parent root = loader.load();
            DibujoController controller = loader.getController();
            if (controller != null) {
                if (predioActivo != null) {
                    controller.activarModoInspeccionPropietario(predioActivo, usuarioActual);
                } else {
                    controller.setUsuarioActual(usuarioActual);
                }
            }
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Levantamiento Georreferenciado");
        } catch (IOException e) {
            mostrarEstadoPerfil("Error al abrir mapa: " + e.getMessage(), "#ef4444");
        }
    }

    @FXML
    public void cerrarSesion(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Iniciar Sesión");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mostrarEstadoPerfil(String texto, String colorHex) {
        if (lblEstadoPerfil != null) {
            lblEstadoPerfil.setText(texto);
            lblEstadoPerfil.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
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
}

