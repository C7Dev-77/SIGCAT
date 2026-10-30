package com.sigcat.utils;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Gestor unificado de navegación y transición de vistas en JavaFX.
 * Preserva el estado maximizado, dimensiones y ubicación de la ventana en pantalla,
 * eliminando parpadeos y cambios bruscos de resolución entre pantallas.
 */
public class ViewNavigator {

    private static final String CSS_PATH = "/styles/main.css";

    /**
     * Cambia la raíz visual conservando la misma ventana, resolución y estado maximizado.
     *
     * @param stage   Escenario actual de la aplicación.
     * @param root    Nuevo nodo raíz a mostrar.
     * @param titulo  Título de la ventana (opcional).
     */
    public static void cambiarVista(Stage stage, Parent root, String titulo) {
        if (stage == null || root == null) return;

        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            if (ViewNavigator.class.getResource(CSS_PATH) != null) {
                scene.getStylesheets().add(ViewNavigator.class.getResource(CSS_PATH).toExternalForm());
            }
            stage.setScene(scene);
        } else {
            // Reemplazar únicamente el nodo raíz para no resetear el tamaño de ventana
            scene.setRoot(root);
            if (scene.getStylesheets().isEmpty() && ViewNavigator.class.getResource(CSS_PATH) != null) {
                scene.getStylesheets().add(ViewNavigator.class.getResource(CSS_PATH).toExternalForm());
            }
        }

        if (titulo != null && !titulo.trim().isEmpty()) {
            stage.setTitle(titulo);
        }

        if (!stage.isShowing()) {
            stage.show();
        }
    }

    /**
     * Obtiene el Stage asociado al evento de acción (por ejemplo, clic en botón).
     */
    public static Stage obtenerStage(ActionEvent event) {
        if (event != null && event.getSource() instanceof Node node && node.getScene() != null) {
            return (Stage) node.getScene().getWindow();
        }
        return null;
    }
}

