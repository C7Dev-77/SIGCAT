# Informe de Ejecución de Pruebas — SIGCAT

**Responsable:** José Sánchez (QA)
**Fecha de última ejecución automatizada:** [completar]
**Resultado de la suite automatizada:** Tests run: 17, Failures: 0, Errors: 0 — BUILD SUCCESS

## 1. Resumen general

| Total de casos documentados | Automatizados (JUnit) | Pendientes de verificación manual |
|---|---|---|
| 28 | 17 | 11 |

## 2. Casos automatizados (JUnit)

Estos casos se ejecutan automáticamente con `.\mvnw.cmd clean test` y no requieren verificación manual.

| ID | Caso de prueba | Clase de prueba | Resultado |
|----|----------------|------------------|-----------|
| CP-09 | Autenticación con credenciales válidas | UsuarioDAOTest | ✅ Pass |
| CP-10 | Autenticación con contraseña incorrecta | UsuarioDAOTest | ✅ Pass |
| — | Autenticación con documento inexistente | UsuarioDAOTest | ✅ Pass |
| CP-11 | Búsqueda de usuario por id existente | UsuarioDAOTest | ✅ Pass |
| CP-12 | Búsqueda de usuario por id inexistente | UsuarioDAOTest | ✅ Pass |
| CP-13 | Cálculo de área (cuadrado 10x10) | ShoelaceCalculatorTest | ✅ Pass |
| — | Cálculo de área (triángulo) | ShoelaceCalculatorTest | ✅ Pass |
| — | Excepción con menos de 3 vértices | ShoelaceCalculatorTest | ✅ Pass |
| — | Resultado estable sin importar el orden de entrada | ShoelaceCalculatorTest | ✅ Pass |
| — | Predios separados no se solapan | SolapamientoValidatorTest | ✅ Pass |
| — | Predios que se cruzan se detectan | SolapamientoValidatorTest | ✅ Pass |
| CP-17 | Predio dentro de otro se detecta | SolapamientoValidatorTest | ✅ Pass |
| CP-25 | Insertar predio con vértices | PredioDAOTest | ✅ Pass |
| CP-26 | Listar solo predios PENDIENTE | PredioDAOTest | ✅ Pass |
| CP-27 | Listar aprobados con vértices cargados | PredioDAOTest | ✅ Pass |
| CP-28 | Actualizar estado y asignar funcionario | PredioDAOTest | ✅ Pass |
| CP-24 | Flujo completo: registrar → aprobar → detectar en nuevo registro | FlujoAprobacionIntegrationTest | ✅ Pass |

## 3. Casos pendientes de verificación manual (interfaz gráfica)

Estos casos requieren interacción directa con la aplicación (clics, ventanas) y se verifican ejecutando la app manualmente. Marcar cada uno al probarlo y adjuntar una captura de pantalla como evidencia en la carpeta `docs/pruebas/evidencias/`.

| ID | Caso de prueba | Estado | Evidencia (nombre de archivo) | Observaciones |
|----|----------------|--------|-------------------------------|----------------|
| CP-01 | Login exitoso - Propietario | ☐ Pendiente | | |
| CP-02 | Login exitoso - Funcionario | ☐ Pendiente | | |
| CP-03 | Contraseña incorrecta muestra error en pantalla | ☐ Pendiente | | |
| CP-04 | Documento inexistente muestra error en pantalla | ☐ Pendiente | | |
| CP-05 | Campo documento vacío - validación | ☐ Pendiente | | |
| CP-06 | Campo contraseña vacío - validación | ☐ Pendiente | | |
| CP-07 | Navegación correcta a menú Propietario | ☐ Pendiente | | |
| CP-08 | Navegación correcta a menú Funcionario | ☐ Pendiente | | |
| CP-14 | No se puede cerrar polígono con menos de 3 puntos | ☐ Pendiente | | |
| CP-15 | Predio sin solapamiento se guarda (mensaje de éxito visible) | ☐ Pendiente | | |
| CP-16 | Predio con solapamiento se rechaza (mensaje de advertencia visible) | ☐ Pendiente | | |
| CP-18 | Botón "Reiniciar dibujo" limpia el Canvas | ☐ Pendiente | | |
| CP-19 | Ventana no se redimensiona durante el dibujo | ☐ Pendiente | | |
| CP-20 | Tabla del funcionario lista pendientes con nombre de propietario | ☐ Pendiente | | |
| CP-21 | Botón "Aprobar" cambia el estado y refresca la tabla | ☐ Pendiente | | |
| CP-22 | Botón "Rechazar" cambia el estado y refresca la tabla | ☐ Pendiente | | |
| CP-23 | Aprobar/Rechazar sin selección muestra mensaje de aviso | ☐ Pendiente | | |

## 4. Cómo ejecutar la suite automatizada