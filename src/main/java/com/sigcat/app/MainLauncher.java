package com.sigcat.app;

/**
 * Clase lanzadora necesaria para evitar el error de JavaFX al usar Fat JAR.
 * Esta clase actúa como entry point real sin extender Application.
 *
 * Responsable: Cristian Morales (Arquitecto)
 */
public class MainLauncher {
    public static void main(String[] args) {
        App.main(args);
    }
}
