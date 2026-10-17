package com.sigcat.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utilidad para manejo seguro de contraseñas con BCrypt.
 * Proporciona métodos estáticos para hashear y verificar contraseñas.
 *
 * Responsable: Cristian Morales (Arquitecto)
 */
public final class PasswordUtil {

    /** Factor de costo (log rounds) para BCrypt. 12 = balance seguridad/rendimiento. */
    private static final int BCRYPT_COST = 12;

    /**
     * Constructor privado para evitar instanciación (clase de utilidad).
     */
    private PasswordUtil() {
        throw new UnsupportedOperationException("Clase de utilidad — no instanciar");
    }

    /**
     * Genera un hash BCrypt seguro de la contraseña proporcionada.
     * Usa un salt aleatorio interno y un factor de costo de 12.
     *
     * @param rawPassword Contraseña en texto plano a hashear. No debe ser null ni vacía.
     * @return Hash BCrypt codificado (incluye salt, versión y costo).
     * @throws IllegalArgumentException si rawPassword es null o está vacía.
     */
    public static String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede ser null ni vacía");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /**
     * Verifica si una contraseña en texto plano coincide con su hash BCrypt almacenado.
     * La verificación es en tiempo constante para evitar ataques de temporización.
     *
     * @param rawPassword  Contraseña en texto plano ingresada por el usuario. No debe ser null ni vacía.
     * @param storedHash   Hash BCrypt almacenado en base de datos. No debe ser null ni vacío.
     * @return true si la contraseña coincide, false en caso contrario.
     * @throws IllegalArgumentException si cualquiera de los parámetros es null o está vacío.
     */
    public static boolean checkPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("La contraseña a verificar no puede ser null ni vacía");
        }
        if (storedHash == null || storedHash.isBlank()) {
            throw new IllegalArgumentException("El hash almacenado no puede ser null ni vacío");
        }
        return BCrypt.checkpw(rawPassword, storedHash);
    }
}