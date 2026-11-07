MATRIZ VIAL DE POPAYAN

La version v6 incorpora GET /api/geografia/matriz-vial.
Al ejecutarlo, el backend consulta OSRM con las 11 coordenadas (3 depositos y 8 puntos rurales de referencia)
y devuelve matrices 11x11 de distancia (km) y duracion (minutos), ademas de la metadata de cada punto.

IMPORTANTE:
- Las coordenadas C1-C3 fueron suministradas por el usuario.
- P1-P8 son centros poblados de referencia, no ubicaciones exactas de fincas.
- OSRM calcula recorridos de la red vial OpenStreetMap; no es una matriz de trafico en tiempo real.
- La API publica de demostracion es para uso ligero. Para uso sostenido/produccion, desplegar OSRM propio
  o contratar un proveedor con SLA. No ejecutar consultas repetitivas innecesarias.
- Esta matriz todavia NO se conecta al optimizador MDVRPTW. Primero debe revisarse que las 121 celdas
  tengan ruta y que los puntos ajustados a la red sean razonables.
