package com.sigcat.models;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Totales de la gestión catastral de un mes, para el resumen mensual
 * del panel del funcionario.
 *
 * <ul>
 *   <li><b>Registrados:</b> solicitudes radicadas en el mes.</li>
 *   <li><b>Pendientes:</b> de las radicadas en el mes, cuántas siguen sin dictamen.</li>
 *   <li><b>Aprobados / Rechazados:</b> dictámenes emitidos en el mes
 *       (según la fecha de revisión), sin importar cuándo se radicaron.</li>
 * </ul>
 *
 * Los predios públicos de la capa base no se cuentan.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class ResumenMensual {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private final int anio;
    private final int mes;
    private final int registrados;
    private final int pendientes;
    private final int aprobados;
    private final int rechazados;

    public ResumenMensual(int anio, int mes, int registrados, int pendientes, int aprobados, int rechazados) {
        this.anio = anio;
        this.mes = mes;
        this.registrados = registrados;
        this.pendientes = pendientes;
        this.aprobados = aprobados;
        this.rechazados = rechazados;
    }

    /** Mes sin movimientos (para mostrar los 12 meses de un año). */
    public static ResumenMensual vacio(int anio, int mes) {
        return new ResumenMensual(anio, mes, 0, 0, 0, 0);
    }

    /** Ej.: "Noviembre 2026". Lo usa la columna "Mes" de la tabla. */
    public String getPeriodo() {
        String nombre = Month.of(mes).getDisplayName(TextStyle.FULL, ES);
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1) + " " + anio;
    }

    public int getAnio() { return anio; }
    public int getMes() { return mes; }
    public int getRegistrados() { return registrados; }
    public int getPendientes() { return pendientes; }
    public int getAprobados() { return aprobados; }
    public int getRechazados() { return rechazados; }

    @Override
    public String toString() {
        return getPeriodo() + "{registrados=" + registrados + ", pendientes=" + pendientes
            + ", aprobados=" + aprobados + ", rechazados=" + rechazados + "}";
    }
}
