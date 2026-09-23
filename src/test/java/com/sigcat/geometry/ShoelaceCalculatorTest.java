package com.sigcat.geometry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.sigcat.models.Vertice;

class ShoelaceCalculatorTest {

    @Test
    void cuadradoDe10x10DebeTenerArea100() {
        List<Vertice> cuadrado = List.of(
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 2, 10, 0),
            new Vertice(1, 3, 10, 10),
            new Vertice(1, 4, 0, 10)
        );

        double area = ShoelaceCalculator.calcularArea(cuadrado);

        assertEquals(100.0, area, 0.0001);
    }

    @Test
    void trianguloRectanguloDebeCalcularAreaCorrecta() {
        // Triángulo con catetos de 6 y 4 -> área = (6*4)/2 = 12
        List<Vertice> triangulo = List.of(
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 2, 6, 0),
            new Vertice(1, 3, 0, 4)
        );

        double area = ShoelaceCalculator.calcularArea(triangulo);

        assertEquals(12.0, area, 0.0001);
    }

    @Test
    void menosDeTresVerticesDebeLanzarExcepcion() {
        List<Vertice> soloDosPuntos = List.of(
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 2, 10, 0)
        );

        assertThrows(IllegalArgumentException.class,
            () -> ShoelaceCalculator.calcularArea(soloDosPuntos));
    }

    @Test
    void ordenDesordenadoDebeDarElMismoResultado() {
        // Mismos puntos que el cuadrado, pero mezclados en la lista
        List<Vertice> desordenado = List.of(
            new Vertice(1, 3, 10, 10),
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 4, 0, 10),
            new Vertice(1, 2, 10, 0)
        );

        double area = ShoelaceCalculator.calcularArea(desordenado);

        assertEquals(100.0, area, 0.0001);
    }
}