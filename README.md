# 🗺️ SIGCAT - Sistema de Registro Gráfico de Predios y Detección Automática de Solapamiento Catastral

**Institución:** Universidad Antonio José de Sucre (UNIAJS)  
**Programa:** Ingeniería de Sistemas | **Asignatura:** Ingeniería de Software  
**Docente:** Ing. Juan Manuel Palacio  

---

## 👥 1. Asignación de Roles del Equipo

| Miembro | Rol | Responsabilidades Clave |
| :--- | :--- | :--- |
| **Cristian Morales** | Técnico y Arquitecto | Arquitectura MVC, integración de módulos, configuración de Maven (`pom.xml`) y empaquetado final (`.jar`). |
| **Andrés Diaz** | Desarrollador Core | Lógica espacial, implementación oficial del algoritmo de Shoelace y motor de validación con JTS Topology Suite. |
| **Leider Barreto** | Desarrollador UI/UX | Vistas en JavaFX (FXML), interacción del Canvas para dibujo de polígonos, feedback visual e integración de JTS. |
| **Aldo Ibañez** | Desarrollador de Persistencia | Diseño de esquema SQLite, implementación del patrón DAO y gestión de datos de predios y vértices. |
| **José Sánchez** | Analista de Calidad (QA) | Diseño de casos de prueba, pruebas unitarias con JUnit y documentación del flujo de aprobación. |

---

## 🎯 2. Alcance del Proyecto

El sistema es un prototipo funcional de escritorio diseñado para agilizar el registro catastral y reducir conflictos de linderos.

### ✅ Incluye:
- **Autenticación:** Acceso basado en roles (Propietario y Funcionario).
- **Captura Gráfica:** Lienzo (Canvas) interactivo para el registro de perímetros.
- **Cálculo Automático:** Determinación de superficie mediante el Algoritmo de Shoelace.
- **Detección de Conflictos:** Validación preventiva de solapamientos usando **JTS Topology Suite**.
- **Flujo de Gestión:** Proceso de solicitud → revisión → aprobación/rechazo por funcionario.

### ❌ No incluye:
- Integración con mapas satelitales reales (se usan coordenadas $x, y$ relativas).
- Despliegue en red o nube (base de datos local embebida).
- Polígonos complejos (con huecos internos o curvas).

---

## 🏗️ 3. Arquitectura y Estructura del Proyecto

Se implementa el patrón **MVC (Model-View-Controller)** para desacoplar la lógica geométrica de la interfaz visual.

```
src/
└── main/
    ├── java/com/sigcat/
    │   ├── app/
    │   │   ├── App.java                      ← Punto de entrada JavaFX (Cristian)
    │   │   └── MainLauncher.java             ← Launcher sin módulos (Cristian)
    │   ├── controllers/
    │   │   ├── LoginController.java          ← Autenticación + navegación por rol ✅ (Leider)
    │   │   ├── MenuPropietarioController.java← Menú propietario + acceso al Canvas ✅ (Leider)
    │   │   ├── MenuFuncionarioController.java← Menú funcionario (stub Fase 4) ✅ (Leider)
    │   │   └── DibujoController.java         ← Canvas interactivo + validación JTS ✅ (Leider)
    │   ├── geometry/
    │   │   ├── ShoelaceCalculator.java       ← Cálculo de área y perímetro con Shoelace ✅ (Andrés)
    │   │   └── SolapamientoValidator.java    ← Validación de solapamiento con JTS Topology Suite ✅ (Andrés)
    │   ├── logic/
    │   │   ├── PoligonoJtsValidator.java     ← Conversión a Polygon JTS para el Canvas ✅ (Leider)
    │   │   └── ShoelaceCalculator.java       ← Cálculo de área en tiempo real para el Canvas ✅ (Leider)
    │   ├── dao/
    │   │   ├── UsuarioDAO.java               ← CRUD + autenticación SQLite ✅ (Aldo)
    │   │   ├── PredioDAO.java                ← CRUD de predios y consultas por estado ✅ (Aldo)
    │   │   └── VerticeDAO.java               ← Inserción y consulta de vértices ✅ (Aldo)
    │   ├── models/
    │   │   ├── Usuario.java                  ✅ (Aldo)
    │   │   ├── Predio.java                   ✅ (Aldo)
    │   │   └── Vertice.java                  ✅ (Aldo)
    │   └── utils/
    │       ├── DatabaseConnection.java       ✅ (Cristian)
    │       └── DatabaseInitializer.java      ✅ (Cristian)
    └── resources/
        ├── fxml/
        │   ├── login.fxml                    ✅ (Leider)
        │   ├── menu-propietario.fxml         ✅ (Leider)
        │   ├── menu-funcionario.fxml         ✅ (Leider — stub Fase 4)
        │   └── dibujo.fxml                   ✅ (Leider)
        └── styles/
            └── main.css                      ✅ (Leider)
```

### 🗄️ Esquema de Base de Datos (SQLite)
1. **Usuarios:** `id_usuario` (PK), `documento`, `nombre`, `password`, `rol` (PROPIETARIO, FUNCIONARIO).
2. **Predios:** `id_predio` (PK), `id_propietario` (FK), `id_funcionario` (FK), `area_calculada` (DOUBLE), `estado` (PENDIENTE, APROBADO, RECHAZADO), `fecha_registro`.
3. **Vertices:** `id_vertice` (PK), `id_predio` (FK), `orden` (INT), `coord_x` (DOUBLE), `coord_y` (DOUBLE).

---

## 📅 4. Fases de Implementación y Estado Actual

### Fase 1: Fundamentos y Arquitectura Base ✅ COMPLETADA
- **Objetivo:** Configuración del entorno y acceso de usuarios.
- **Completado:**
  - ✅ Configuración de Maven con JavaFX 21, SQLite, JTS 1.20, JUnit 5
  - ✅ Script de inicialización SQLite (`DatabaseInitializer`)
  - ✅ DAO de conexión (`DatabaseConnection`, `UsuarioDAO`)
  - ✅ Pantalla de Login con navegación real por rol (`LoginController`, `login.fxml`)
  - ✅ Menú Propietario (`MenuPropietarioController`, `menu-propietario.fxml`)
  - ✅ Menú Funcionario stub (`MenuFuncionarioController`, `menu-funcionario.fxml`)

### Fase 2: Módulo Gráfico y Geometría Básica ✅ COMPLETADA (Leider)
- **Objetivo:** Dibujo de predios y cálculo de superficies.
- **Completado:**
  - ✅ Canvas interactivo con clic para añadir vértices (`DibujoController`, `dibujo.fxml`)
  - ✅ Cierre automático del polígono al hacer clic cerca del primer punto
  - ✅ Cálculo de área con Shoelace (`ShoelaceCalculator` — temporal hasta que Andrés entregue)
  - ⏳ **Pendiente (Andrés):** Entregar `ShoelaceCalculator` oficial con la misma firma `calcularArea(List<Point2D>)`

### Fase 3: Lógica Espacial y Persistencia de Vértices ✅ COMPLETADA (Leider - JTS)
- **Objetivo:** Implementación de la detección de solapamientos.
- **Completado:**
  - ✅ Integración de JTS Topology Suite (`PoligonoJtsValidator`)
  - ✅ Detección de intersección entre el polígono nuevo y los predios aprobados
  - ⏳ **Pendiente (Aldo):** Alimentar `DibujoController.setPrediosAprobados(...)` con los polígonos de la BD antes de mostrar la pantalla de dibujo
  - ⏳ **Pendiente (Aldo):** Guardar predio + vértices en estado PENDIENTE al cerrar el polígono sin conflictos

### Fase 4: Flujo de Aprobación y Entrega ⏳ PENDIENTE
- **Objetivo:** Gestión del funcionario y empaquetado final.
- **Pendiente:**
  - ⏳ Panel de administración de solicitudes (Andrés): `TableView` de PENDIENTES, botones Aprobar/Rechazar en `menu-funcionario.fxml`
  - ⏳ Configuración final del Fat JAR (Cristian)
  - ⏳ Pruebas End-to-End (José)

---

## 🔌 5. Puntos de Integración entre Miembros

> Estos son los contratos de interfaz que deben respetarse para que los módulos se conecten sin conflictos.

| Punto de integración | Quién produce | Quién consume | Contrato |
| :--- | :--- | :--- | :--- |
| Predios aprobados → Canvas | **Aldo** | **Leider** (`DibujoController`) | Llamar `controller.setPrediosAprobados(List<Polygon>)` antes de mostrar `dibujo.fxml`. Convertir vértices de BD con `PoligonoJtsValidator.crearPoligono(...)` |
| Guardar predio PENDIENTE | **Leider** invoca | **Aldo** implementa | En `DibujoController.cerrarPoligono()` (línea marcada con TODO), Aldo conecta la persistencia de `predios` + `vertices` |
| `ShoelaceCalculator` oficial | **Andrés** entrega | **Leider** (`DibujoController`) | Misma firma: `public static double calcularArea(List<Point2D>)`. Reemplazar `ShoelaceCalculator.java` en `com.sigcat.logic` |
| Panel funcionario | **Andrés** implementa | `menu-funcionario.fxml` | Completar `MenuFuncionarioController` con `TableView` y `PredioDAO` |
| Validación async (riesgo UI) | **Cristian** | `DibujoController` | Mover la llamada a `validadorJts.existeSolapamiento(...)` a un `Task<Boolean>` con `Platform.runLater` cuando `prediosAprobados` crezca |

---

## ⚠️ 6. Gestión de Riesgos Técnicos

| Riesgo | Nivel | Plan de Mitigación | Responsable |
| :--- | :---: | :--- | :--- |
| Bloqueo del hilo de UI al validar solapamientos | Alto | Usar `Task<Boolean>` y `Platform.runLater` en `DibujoController` | Cristian |
| Curva de aprendizaje de JTS Topology Suite | Medio | ✅ Resuelto — `PoligonoJtsValidator` operativo | ~~Leider~~ |
| Deformación del polígono al redimensionar ventana | Medio | Canvas fijo 1024×768 px en `dibujo.fxml` | Andrés |

---

## ✅ 7. Criterios de Terminación (Definition of Done)

Una tarea se considera finalizada solo si:
1. El código compila sin errores en **Java 17 LTS**.
2. La funcionalidad no afecta secciones previamente operativas (no hay regresiones).
3. El código ha sido integrado en la rama `main` del repositorio.
4. No existen rutas de archivos o credenciales *hardcoded* (configuración portable).

---

## 🎨 8. Guía de Diseño y Paleta de Colores

Definida por Leider Barreto en `src/main/resources/styles/main.css`:

| Token | Valor | Uso |
| :--- | :--- | :--- |
| Fondo principal | `#1a1a2e` | Pantallas de login y menús |
| Fondo secundario | `#16213e` | Campos de texto, toolbar del Canvas |
| Fondo profundo | `#0d0d1a` | Área del Canvas de dibujo |
| Acento / error | `#e94560` | Botón primario, alertas de solapamiento |
| Texto principal | `#ffffff` | Títulos y textos destacados |
| Texto secundario | `#a0a0c0` | Labels, subtítulos, info del Canvas |

---

## 🚀 9. Cómo Ejecutar el Proyecto

### Requisitos
- Java 17 LTS
- Maven 3.8+

### Ejecutar en desarrollo
```bash
mvn javafx:run
```

### Compilar (sin ejecutar)
```bash
mvn compile
```

### Probar el Canvas de dibujo
1. Ejecuta la app con `mvn javafx:run`.
2. Inicia sesión como **propietario** (documento registrado en la BD).
3. Haz clic en **"Registrar nuevo predio"**.
4. Haz clic en el Canvas para añadir vértices (mínimo 3).
5. Haz clic cerca del círculo naranja (primer vértice) para cerrar el polígono.
6. Verás el área calculada y el estado de solapamiento.

---

## 📋 10. Tareas Pendientes por Miembro

### Andrés Diaz
- [ ] Entregar `ShoelaceCalculator.java` oficial (`com.sigcat.logic`, misma firma de método).
- [ ] Implementar el panel del Funcionario en `MenuFuncionarioController` + actualizar `menu-funcionario.fxml`.

### Aldo Ibañez
- [ ] Llamar `dibujoController.setPrediosAprobados(...)` antes de mostrar `dibujo.fxml` en `MenuPropietarioController.irADibujar()`.
- [ ] Implementar la persistencia del predio + vértices en estado PENDIENTE al cerrar el polígono sin conflictos (conectar el TODO en `DibujoController.cerrarPoligono()`).

### Cristian Morales
- [ ] Mover la validación de solapamiento a un `Task<Boolean>` para no bloquear el hilo de UI.
- [ ] Configurar el Fat JAR ejecutable final (Fase 4).

### José Sánchez (QA)
- [ ] Casos de prueba para `PoligonoJtsValidator` (polígonos solapados, adyacentes, disjuntos).
- [ ] Casos de prueba para `ShoelaceCalculator` (triángulos, cuadrados, polígonos irregulares).
- [ ] Pruebas End-to-End del flujo completo: login → dibujo → persistencia → aprobación.
