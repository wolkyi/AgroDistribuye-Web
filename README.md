# AgroDistribuye Web 0.8.4

Aplicación académica de apoyo a decisiones para distribución agrícola en Popayán. Incluye editor de datos, importación CSV/JSON, siete escenarios temporales, dos alternativas operativas, sensibilidad de tres parámetros, comparación económica, exportación y modo sin internet.

Los datos operativos y económicos del ejemplo son simulados. Las coordenadas de productores son referencias rurales; los depósitos son candidatos cuya operación debe validarse. El escenario A es una referencia del modelo y no la operación real de una organización.

La app utiliza nombres genéricos: C1 = Distribuidora Norte, C2 = Distribuidora Centro, C3 = Distribuidora Sur y Productor P1–P8. Las coordenadas de referencia, distancias y tiempos se conservan. Las geometrías se identifican por códigos estables, independientes del nombre mostrado. Las ejecuciones antiguas guardadas en el navegador deben recalcularse para usar la nueva nomenclatura.

## Ejecutar en desarrollo

Requisitos: Java 25 o superior (verificado con JDK 26), Maven 3.9+ y dependencias descargadas la primera vez.

```powershell
mvn test
node --test src/test/js/workspace.test.cjs
mvn spring-boot:run
```

Abrir http://localhost:8080. El modo local está activado por defecto. Se puede editar o importar el ejemplo en `data/ejemplo-planificacion.csv` y `.json`.

## Preparar la demostración sin internet

Con conexión disponible durante la compilación:

```powershell
mvn package spring-boot:repackage
```

Después, con Java compatible instalado y el puerto libre:

```powershell
java -jar target/agrodistribuye-web-0.8.4.jar
```

El JAR incluye matriz OSRM de referencia, geometrías disponibles, coordenadas y librerías cartográficas locales. No necesita Maven ni servicios externos al ejecutar en modo local. Las rutas sin geometría guardada se representan como esquemas discontinuos, sin reemplazar las distancias de la matriz.

## Uso y alcance

1. Editar datos o importar CSV/JSON en Configurar la jornada.
2. Validar las entradas y declarar su procedencia; el costo observado del plan actual es opcional.
3. Elegir modo local/en línea, límite de resolución y estudios adicionales.
4. Ejecutar y consultar el progreso. Con todas las opciones hay 15 escenarios.
5. Comparar costos, horarios, servicio, utilización y brecha de optimalidad; exportar CSV o JSON.

Los datos y la última ejecución se conservan en este navegador. El JSON exportado es el respaldo portátil. Julia/JuMP y el contraste con otra herramienta están pendientes de confirmación.

Consultar [OPERACION_ESTUDIO.md](OPERACION_ESTUDIO.md) para formato CSV, supuestos, métricas, estados, arquitectura, API y límites. `EXPERIMENTOS_TIEMPOS.md` conserva el contexto de los siete escenarios temporales; la descripción actual del modo local y los experimentos ampliados está en OPERACION_ESTUDIO.md.
