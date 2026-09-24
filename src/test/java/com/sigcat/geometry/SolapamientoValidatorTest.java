package com.sigcat.geometry;

import com.sigcat.models.Vertice;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolapamientoValidatorTest {

    @Test
    void dosCuadradosSeparadosNoDebenSolaparse() {
        // Cuadrado A: de (0,0) a (10,10)
        List<Vertice> a = List.of(
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 2, 10, 0),
            new Vertice(1, 3, 10, 10),
            new Vertice(1, 4, 0, 10)
        );

        // Cuadrado B: de (20,20) a (30,30) -- bien lejos del A
        List<Vertice> b = List.of(
            new Vertice(2, 1, 20, 20),
            new Vertice(2, 2, 30, 20),
            new Vertice(2, 3, 30, 30),
            new Vertice(2, 4, 20, 30)
        );

        Polygon poligonoA = SolapamientoValidator.construirPoligono(a);
        Polygon poligonoB = SolapamientoValidator.construirPoligono(b);

        boolean resultado = SolapamientoValidator.existeSolapamiento(
            poligonoA, List.of(poligonoB));

        assertFalse(resultado, "Predios separados no deberían solaparse");
    }

    @Test
    void dosCuadradosQueSeCruzanDebenSolaparse() {
        // Cuadrado A: de (0,0) a (10,10)
        List<Vertice> a = List.of(
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 2, 10, 0),
            new Vertice(1, 3, 10, 10),
            new Vertice(1, 4, 0, 10)
        );

        // Cuadrado B: de (5,5) a (15,15) -- se cruza con A en la esquina
        List<Vertice> b = List.of(
            new Vertice(2, 1, 5, 5),
            new Vertice(2, 2, 15, 5),
            new Vertice(2, 3, 15, 15),
            new Vertice(2, 4, 5, 15)
        );

        Polygon poligonoA = SolapamientoValidator.construirPoligono(a);
        Polygon poligonoB = SolapamientoValidator.construirPoligono(b);

        boolean resultado = SolapamientoValidator.existeSolapamiento(
            poligonoA, List.of(poligonoB));

        assertTrue(resultado, "Predios que se cruzan deberían detectarse como solapados");
    }

    @Test
    void predioCompletamenteDentroDeOtroDebeDetectarse() {
        // Cuadrado grande: de (0,0) a (20,20)
        List<Vertice> grande = List.of(
            new Vertice(1, 1, 0, 0),
            new Vertice(1, 2, 20, 0),
            new Vertice(1, 3, 20, 20),
            new Vertice(1, 4, 0, 20)
        );

        // Cuadrado chico, completamente adentro del grande: de (5,5) a (10,10)
        List<Vertice> chico = List.of(
            new Vertice(2, 1, 5, 5),
            new Vertice(2, 2, 10, 5),
            new Vertice(2, 3, 10, 10),
            new Vertice(2, 4, 5, 10)
        );

        Polygon poligonoGrande = SolapamientoValidator.construirPoligono(grande);
        Polygon poligonoChico = SolapamientoValidator.construirPoligono(chico);

        boolean resultado = SolapamientoValidator.existeSolapamiento(
            poligonoGrande, List.of(poligonoChico));

        assertTrue(resultado,
            "Un predio completamente contenido en otro debe detectarse como conflicto");
    }
}