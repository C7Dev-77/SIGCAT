# 🗺️ SIGCAT - Sistema de Registro Gráfico de Predios y Detección Automática de Solapamiento Catastral

**Institución:** Universidad Antonio José de Sucre (UNIAJS)  
**Programa:** Ingeniería de Sistemas | **Asignatura:** Ingeniería de Software  
**Docente:** Ing. Juan Manuel Palacio  

---

## 👥 1. Asignación de Roles del Equipo

El proyecto se desarrolla bajo un enfoque ágil, distribuyendo las responsabilidades de la siguiente manera:

| Miembro | Rol | Responsabilidades Clave |
| :--- | :--- | :--- |
| **Cristian Morales** | Técnico y Arquitecto | Arquitectura MVC, integración de módulos, configuración de Maven (`pom.xml`) y empaquetado final (`.jar`). |
| **Andrés Diaz** | Desarrollador Core | Lógica espacial, implementación del algoritmo de Shoelace y motor de validación con JTS Topology Suite. |
| **Leider Barreto** | Desarrollador UI/UX | Vistas en JavaFX (FXML), interacción del Canvas para dibujo de polígonos y feedback visual. |
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
- **Flujo de Gestión:** Proceso de solicitud $\rightarrow$ revisión $\rightarrow$ aprobación/rechazo por funcionario.

### ❌ No incluye:
- Integración con mapas satelitales reales (se usan coordenadas $x, y$ relativas).
- Despliegue en red o nube (base de datos local embebida).
- Polígonos complejos (con huecos internos o curvas).

---

## 🏗️ 3. Arquitectura y Modelo de Datos

Se implementa el patrón **MVC (Model-View-Controller)** para desacoplar la lógica geométrica de la interfaz visual.

### 🗄️ Esquema de Base de Datos (SQLite)
1. **Usuarios:** `id_usuario` (PK), `documento`, `nombre`, `password`, `rol` (PROPIETARIO, FUNCIONARIO).
2. **Predios:** `id_predio` (PK), `id_propietario` (FK), `id_funcionario` (FK), `area_calculada` (DOUBLE), `estado` (PENDIENTE, APROBADO, RECHAZADO), `fecha_registro`.
3. **Vertices:** `id_vertice` (PK), `id_predio` (FK), `orden` (INT), `coord_x` (DOUBLE), `coord_y` (DOUBLE).

---

## 📅 4. Fases de Implementación y Cronograma

El desarrollo se divide en 4 Sprints de dos semanas cada uno:

### Fase 1: Fundamentos y Arquitectura Base
- **Objetivo:** Configuración del entorno y acceso de usuarios.
- **Hitos:** Configuración de Maven, script de SQLite, DAO de conexión y pantalla de Login.
- **Entregable:** Repositorio funcional con sistema de autenticación operativo.

### Fase 2: Módulo Gráfico y Geometría Básica
- **Objetivo:** Dibujo de predios y cálculo de superficies.
- **Hitos:** Implementación de Canvas interactivo, clase `ShoelaceCalculator.java` y pruebas unitarias de área.
- **Entregable:** Interfaz que permite dibujar polígonos y muestra el área calculada.

### Fase 3: Lógica Espacial y Persistencia de Vértices
- **Objetivo:** Implementación de la detección de solapamientos.
- **Hitos:** Integración de JTS Topology Suite, inserción transaccional de vértices y pruebas de estrés geométrico.
- **Entregable:** Bloqueo automático de registros que invadan territorios ya aprobados.

### Fase 4: Flujo de Aprobación y Entrega
- **Objetivo:** Gestión del funcionario y empaquetado final.
- **Hitos:** Panel de administración de solicitudes, configuración de Maven Shade Plugin (Fat JAR) y pruebas End-to-End.
- **Entregable:** Archivo `.jar` ejecutable, manual de usuario y código fuente.

---

## ⚠️ 5. Gestión de Riesgos Técnicos

| Riesgo | Nivel | Plan de Mitigación | Responsable |
| :--- | :---: | :--- | :--- |
| Bloqueo del hilo de UI al validar solapamientos | Alto | Uso de bloques asíncronos (`Task` y `Platform.runLater`) | Cristian |
| Curva de aprendizaje de JTS Topology Suite | Medio | Estudio anticipado de la API y scripts de prueba independientes | Leider |
| Deformación del polígono al redimensionar ventana | Medio | Bloqueo del redimensionamiento (Canvas fijo 1024x768 px) | Andrés |

---

## ✅ 6. Criterios de Terminación (Definition of Done)

Una tarea se considera finalizada solo si:
1. El código compila sin errores en **Java 17 LTS**.
2. La funcionalidad no afecta secciones previamente operativas (no hay regresiones).
3. El código ha sido integrado en la rama `main` del repositorio.
4. No existen rutas de archivos o credenciales *hardcoded* (configuración portable).

---

## 🎨 7. Guía de Diseño y Tareas UI/UX (Responsable: Leider Barreto)

En la carpeta `docs/diseno/` se encuentran las maquetas de alta fidelidad que sirven como guía visual para el desarrollo de la interfaz:

| Maqueta | Archivo | Descripción |
| :--- | :--- | :--- |
| **Login** | `docs/diseno/1_login_ui.jpg` | Vista de autenticación con tarjeta centralizada y tema `#1a1a2e`. *(Completada ✅)* |
| **Dashboard Propietario** | `docs/diseno/2_dashboard_propietario.jpg` | Panel para ingresar coordenadas $(X, Y)$, lista de vértices ($P_1$ a $P_n$) y dibujo de polígonos irregulares. |
| **Dashboard Funcionario** | `docs/diseno/3_dashboard_funcionario.jpg` | Tabla de solicitudes pendientes, visor preliminar del predio y botones *Aprobar* / *Rechazar*. |
| **Alerta de Conflicto** | `docs/diseno/4_deteccion_solapamiento.jpg` | Resaltado en rojo neón (`#e94560`) cuando se detecta solapamiento entre predios. |
| **Mapa Urbano con Calles** | `docs/diseno/5_mapa_con_calles_urbano.jpg` | Visor catastral con calles en líneas blancas y manzanas de fondo (Sector Sincelejo / MAGNA-SIRGAS). |

### 📋 Próximas Tareas de Leider (Sprint 1 - Fase 1 & 2):
1. **Crear `dashboard-funcionario.fxml`:**
   - Ubicación: `src/main/resources/fxml/dashboard-funcionario.fxml`.
   - Elementos: `TableView` para solicitudes (`ID`, `Propietario`, `Área`, `Estado`, `Fecha`), panel de inspección con visor y botones `btnAprobar` y `btnRechazar`.
2. **Crear `dashboard-propietario.fxml`:**
   - Ubicación: `src/main/resources/fxml/dashboard-propietario.fxml`.
   - Elementos: Formulario para ingresar coordenadas $X$ e $Y$, `TableView` de vértices añadidos, etiqueta de área calculada y botón `btnEnviarSolicitud`.
3. **Conectar la navegación en `LoginController.java`:**
   - Reemplazar el diálogo informativo temporal por la carga de la escena correspondiente con `FXMLLoader` y `stage.setScene(...)`.
4. **Lienzo del Mapa (Canvas):**
   - Implementar el fondo oscuro con la cuadrícula / calles vectoriales y el renderizado de polígonos con JavaFX `GraphicsContext`.

> 💡 **Prompt recomendado para Leider (si usa ChatGPT / Claude):**
> *"Actúa como desarrollador JavaFX 17 Senior. Necesito crear el archivo FXML y el controlador para el dashboard del [Funcionario/Propietario] de SIGCAT, un sistema catastral de predios. Debe usar la paleta de colores de nuestro `main.css` (fondo oscuro `#1a1a2e`, acentos `#e94560`, textos `#ffffff` y `#a0a0c0`). Debe contener [especificar componentes según la guía]. Proporciona el código FXML limpio y compatible con Java 17 sin librerías externas adicionales."*

