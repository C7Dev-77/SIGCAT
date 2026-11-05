package com.sigcat.utils;

import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

/**
 * Agrega a un PasswordField la opción de mostrar u ocultar lo escrito.
 *
 * El PasswordField debe estar dentro de un StackPane (así está en el FXML).
 * Se crea encima un TextField "gemelo" con el mismo texto, estilo y ayuda;
 * el botón alterna cuál de los dos se ve. El texto es siempre el mismo,
 * así que el controlador sigue leyendo el PasswordField como antes.
 */
public final class PasswordToggle {

    private PasswordToggle() {}

    public static void vincular(PasswordField campo, Button boton) {
        if (campo == null || boton == null || !(campo.getParent() instanceof StackPane contenedor)) {
            return;
        }

        TextField visible = new TextField();
        visible.getStyleClass().setAll(campo.getStyleClass());
        visible.getStyleClass().remove("password-field");
        visible.setPromptText(campo.getPromptText());
        visible.setMaxWidth(Double.MAX_VALUE);
        visible.textProperty().bindBidirectional(campo.textProperty());
        visible.setOnAction(campo.getOnAction());
        visible.setVisible(false);
        visible.setManaged(false);
        visible.disableProperty().bind(campo.disableProperty());
        contenedor.getChildren().add(visible);

        boton.setText("Mostrar");
        boton.setFocusTraversable(false);
        boton.setOnAction(e -> {
            boolean mostrar = !visible.isVisible();
            visible.setVisible(mostrar);
            visible.setManaged(mostrar);
            campo.setVisible(!mostrar);
            campo.setManaged(!mostrar);
            boton.setText(mostrar ? "Ocultar" : "Mostrar");

            // Mantener el foco y el cursor al final del texto en el campo que queda visible.
            TextField activo = mostrar ? visible : campo;
            activo.requestFocus();
            activo.positionCaret(activo.getText() == null ? 0 : activo.getText().length());
        });
    }
}
