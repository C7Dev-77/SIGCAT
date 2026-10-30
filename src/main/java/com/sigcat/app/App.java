package com.sigcat.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Clase principal de la aplicación JavaFX - SIGCAT.
 * Carga la pantalla de Login como vista inicial.
 *
 * Responsable: Cristian Morales (Arquitecto)
 */
public class App extends Application {

    @Override
    public void init() throws Exception {
        com.sigcat.utils.DatabaseInitializer.initialize();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/login.fxml")
        );
        Scene scene = new Scene(loader.load(), 960, 640);
        scene.getStylesheets().add(
            getClass().getResource("/styles/main.css").toExternalForm()
        );

        primaryStage.setTitle("SIGCAT — Sistema Catastral Multipropósito (Sincelejo, Sucre)");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(700);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
