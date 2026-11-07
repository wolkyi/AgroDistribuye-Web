# AgroDistribuye – Experimentos de tiempos de espera y operación

## Alcance

Esta modificación agrega programación flexible de salida y tres familias de experimentos al motor MDVRPTW. Los datos de demanda, inventario, capacidades, ventanas de productores, tiempos de servicio, costos base y matriz vial se mantienen sin cambios.

Todos los coeficientes de penalización de este documento son **supuestos económicos simulados para análisis académico**. No representan tarifas, salarios ni costos verificados de las empresas candidatas a depósito.

## Convención temporal

- Inicio de jornada: 07:00 (420 minutos desde medianoche).
- Cierre máximo: 18:00 (1080 minutos desde medianoche).
- `departureTime`: salida efectiva programada del depósito.
- `depotWaitingMinutes`: minutos entre 07:00 y la salida.
- `arrivalTime`: llegada vial estimada a cada productor.
- `waitingMinutes` en cada parada y en la ruta: espera entre llegada e inicio del servicio.
- `operationMinutes`: regreso menos salida; incluye conducción, servicios y esperas en ruta, pero excluye la espera previa en depósito.

La espera en depósito se interpreta bajo el supuesto acordado de que la jornada laboral comienza a las 07:00. No se modela aún una actividad de cargue independiente.

## Escenarios

### A – Referencia original

- Salida fija a las 07:00.
- Objetivo: costos fijos de vehículo + costos por kilómetro.
- Mantiene el comportamiento temporal original para servir como baseline.

### B – Penalización de esperas

- Salida flexible entre 07:00 y 18:00.
- Objetivo: costo económico base + penalización de espera en depósito + penalización de espera en productores.
- Sensibilidad ejecutada con estos valores simulados:

| Variante | Depósito (COP/min) | Productor (COP/min) |
|---|---:|---:|
| B1 | 50 | 200 |
| B2 | 100 | 200 |
| B3 | 300 | 200 |

Se varía el costo relativo de la espera en depósito y se mantiene en 200 COP/min el valor de referencia de espera en productor.

### C – Penalización de duración de operación

- Salida flexible entre 07:00 y 18:00.
- Objetivo: costo económico base + penalización de duración desde salida hasta regreso.
- Sensibilidad simulada:

| Variante | Operación (COP/min) |
|---|---:|
| C1 | 50 |
| C2 | 100 |
| C3 | 200 |

## Interpretación económica

`totalCostCop` representa exclusivamente el costo logístico base (costo fijo + costo por distancia). `objectiveValue` representa el valor de la función objetivo, que puede incluir penalizaciones temporales y por eso debe interpretarse como COP-equivalentes del experimento, no como factura o costo real pagado.

## Validación

La validación independiente sigue comprobando asignación única, continuidad de rutas, capacidades, inventario por producto y depósito, ventanas horarias y regreso antes de las 18:00. También verifica que la salida no sea anterior a las 07:00 ni posterior al regreso.

## Respuesta del servicio

El endpoint existente `POST /api/optimizaciones/ejecutar` conserva los campos principales de la solución A en el nivel superior y agrega `experiments` con A, B1–B3 y C1–C3. Las geometrías OSRM se solicitan para las rutas de los siete escenarios. Una caché en memoria comparte consultas de secuencias idénticas (depósito y productores en orden); los fallos se retiran de la caché para permitir reintentos. Un fallo de geometría conserva las métricas y la validación del escenario e incluye una advertencia visible en la interfaz.

## Limitaciones conocidas

- La matriz OSRM sigue siendo la fuente de distancias y tiempos estimados; no incorpora tráfico en tiempo real.
- Los coeficientes son escenarios simulados y deben identificarse así en cualquier informe.
- Los siete escenarios se ejecutan en paralelo, con un solucionador independiente por escenario y concurrencia limitada por los procesadores disponibles. El JSON conserva el orden A, B1–B3, C1–C3. Las consultas de geometría usan un grupo separado de hasta cuatro hilos.
- El código debe compilarse y ejecutarse en el entorno del proyecto con Java 25, Maven y OR-Tools 9.12.4544 antes de considerar la modificación verificada.

## Interfaz y tiempos de ejecución

Las pestañas seleccionan las métricas, rutas y geometrías de cada escenario; admiten navegación con flechas, Inicio y Fin. El ahorro corresponde a 100 × (operación A − operación seleccionada) / operación A y solo se calcula si ambos escenarios están validados y A tiene operación positiva. Un valor negativo indica mayor duración. La tabla conserva los siete resultados para comparación.

Cada vehículo muestra salida, regreso, espera en depósito, espera en productores, conducción, servicio y operación. La operación excluye la espera previa en depósito. El campo solvingTimeMs de cada experimento es el tiempo del solucionador; el campo del nivel superior mide la ejecución completa, incluida la matriz y las geometrías OSRM.
