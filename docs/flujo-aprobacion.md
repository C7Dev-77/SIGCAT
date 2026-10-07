# Flujo de Aprobación de Predios — SIGCAT

Documentado por: José Sánchez (QA)

## Descripción general

El registro de un predio nunca queda oficial de forma automática. Todo predio nuevo pasa obligatoriamente por una validación geométrica automática y luego por una revisión humana antes de considerarse parte del catastro.

## Diagrama de flujo

```mermaid
flowchart TD
    A[Propietario inicia sesión] --> B[Dibuja el perímetro en el Canvas]
    B --> C[Cierra el polígono]
    C --> D[Sistema calcula área - Shoelace]
    D --> E{¿Se solapa con un predio APROBADO?}
    E -- Sí --> F[Se rechaza el registro.\nNo se guarda en la base de datos]
    E -- No --> G[Se guarda el predio.\nEstado: PENDIENTE]
    G --> H[Funcionario inicia sesión]
    H --> I[Ve la solicitud en el panel de pendientes]
    I --> J{Decisión del funcionario}
    J -- Aprobar --> K[Estado: APROBADO\nQueda disponible para validar\nsolapamiento de futuros predios]
    J -- Rechazar --> L[Estado: RECHAZADO]
```

## Estados posibles de un predio

| Estado | Significado | ¿Quién lo asigna? |
|--------|-------------|---------------------|
| PENDIENTE | El predio pasó la validación geométrica automática, pero aún no ha sido revisado por un humano | Sistema, automáticamente al guardar |
| APROBADO | Un funcionario confirmó la solicitud. A partir de este momento, el predio se usa como referencia para detectar solapamientos en futuros registros | Funcionario catastral |
| RECHAZADO | Un funcionario rechazó la solicitud | Funcionario catastral |

## Reglas de negocio clave

1. **La medición del propietario nunca es la fuente de verdad final.** Solo pasa a ser oficial cuando un funcionario la aprueba.
2. **Un predio en estado PENDIENTE no afecta la validación de solapamiento.** Solo los predios APROBADO se comparan contra nuevos registros — esto evita que dos solicitudes pendientes y en conflicto entre sí bloqueen indefinidamente el sistema.
3. **La detección de solapamiento ocurre ANTES de guardar**, no después. Si hay conflicto, el predio ni siquiera llega a existir en la base de datos como PENDIENTE.
4. **Toda aprobación o rechazo queda vinculado al funcionario** que tomó la decisión (trazabilidad).