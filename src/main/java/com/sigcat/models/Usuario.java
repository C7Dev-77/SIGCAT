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
    private Rol rol = Rol.PROPIETARIO;

    public Usuario() {}

    public Usuario(String documento, String nombre, String password, Rol rol) {
        this.documento = documento;
        this.nombre = nombre;
        this.password = password;
        this.rol = rol;
    }

    public Usuario(String documento, String nombre, String password, String rol) {
        this.documento = documento;
        this.nombre = nombre;
        this.password = password;
        setRol(rol);
    }

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

    public String getPasswordHash() { return password; }
    public void setPasswordHash(String passwordHash) { this.password = passwordHash; }

    public String getRol() {
        return rol != null ? rol.name() : "PROPIETARIO";
    }

    public Rol getRolEnum() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public void setRol(String rolStr) {
        if (rolStr != null) {
            try {
                this.rol = Rol.valueOf(rolStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                this.rol = Rol.PROPIETARIO;
            }
        }
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", documento='" + documento + "', nombre='" + nombre + "', rol=" + rol + "}";
    }
}
