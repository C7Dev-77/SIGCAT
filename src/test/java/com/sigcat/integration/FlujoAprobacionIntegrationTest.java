package com.sigcat.integration;

import com.sigcat.geometry.ShoelaceCalculator;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Vertice;
import com.sigcat.security.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de pruebas automatizadas para certificar la precisión matemática
 * del algoritmo de Shoelace, la detección de solapamientos y las reglas de negocio.
 */
public class FlujoAprobacionIntegrationTest {

    @Test
    @DisplayName("CP-GEO-01: Cálculo exacto de área y perímetro en polígono rectangular")
    public void testCalculoShoelaceRectangulo() {
        List<Vertice> rectangulo = Arrays.asList(
                new Vertice(0, 0, 1),
                new Vertice(10, 0, 2),
                new Vertice(10, 5, 3),
                new Vertice(0, 5, 4)
        );

        double area = ShoelaceCalculator.calcularArea(rectangulo);
        double perimetro = ShoelaceCalculator.calcularPerimetro(rectangulo);

        assertEquals(50.0, area, 0.001, "El área de un rectángulo de 10x5 debe ser 50");
        assertEquals(30.0, perimetro, 0.001, "El perímetro de un rectángulo de 10x5 debe ser 30");
    }

    @Test
    @DisplayName("CP-GEO-02: Detección positiva de solapamiento parcial entre dos predios")
    public void testSolapamientoParcialDetectado() {
        List<Vertice> predioBase = Arrays.asList(
                new Vertice(0, 0, 1),
                new Vertice(100, 0, 2),
                new Vertice(100, 100, 3),
                new Vertice(0, 100, 4)
        );

        List<Vertice> predioInvasor = Arrays.asList(
                new Vertice(50, 50, 1),
                new Vertice(150, 50, 2),
                new Vertice(150, 150, 3),
                new Vertice(50, 150, 4)
        );

        assertTrue(SolapamientoValidator.PoligonosSeSolapan(predioBase, predioInvasor),
                "Debe detectar solapamiento cuando un predio invade parcialmente el área de otro.");
    }

    @Test
    @DisplayName("CP-GEO-03: Predios colindantes o separados no deben marcar solapamiento")
    public void testPrediosDisjuntosValidos() {
        List<Vertice> predioA = Arrays.asList(
                new Vertice(0, 0, 1),
                new Vertice(50, 0, 2),
                new Vertice(50, 50, 3),
                new Vertice(0, 50, 4)
        );

        List<Vertice> predioB = Arrays.asList(
                new Vertice(60, 0, 1),
                new Vertice(110, 0, 2),
                new Vertice(110, 50, 3),
                new Vertice(60, 50, 4)
        );

        assertFalse(SolapamientoValidator.PoligonosSeSolapan(predioA, predioB),
                "Dos predios separados no deben reportar conflicto topológico.");
    }

    @Test
    @DisplayName("CP-SEC-01: Hashing seguro con factor de costo 12 y verificación de contraseña")
    public void testPasswordHashingCost12() {
        String claveOriginal = "Sincelejo2026*";
        String hash = PasswordUtil.hashPassword(claveOriginal);

        assertNotNull(hash, "El hash generado no debe ser nulo.");
        assertTrue(hash.startsWith("$2a$12$"), "El hash debe incluir el identificador BCrypt y factor de costo 12.");
        assertTrue(PasswordUtil.checkPassword(claveOriginal, hash), "Debe validar positivamente la contraseña correcta.");
        assertFalse(PasswordUtil.checkPassword("ClaveIncorrecta", hash), "Debe rechazar una contraseña incorrecta.");
    }
}
