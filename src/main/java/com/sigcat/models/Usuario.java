package com.sigcat.models;

/**
 * Modelo de dominio que representa a un usuario del sistema.
 * Roles posibles: PROPIETARIO, FUNCIONARIO.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 */
public class Usuario {

    public enum Rol {
        PROPIETARIO,
        FUNCIONARIO
    }

    private int id;
    private String documento;
    private String nombre;
    private String password;
    private Rol rol;

    public Usuario() {}

    public Usuario(int id, String documento, String nombre, String password, Rol rol) {
        this.id = id;
        this.documento = documento;
        this.nombre = nombre;
        this.password = password;
        this.rol = rol;
    }

    // ─── Getters y Setters ───────────────────────────────────────
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", documento='" + documento + "', nombre='" + nombre + "', rol=" + rol + "}";
    }
}
