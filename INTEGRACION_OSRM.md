# Integración OSRM + OR-Tools

La ejecución de `/api/optimizaciones/ejecutar` solicita la matriz de conducción OSRM de 11 puntos y la entrega al modelo MDVRPTW. Distancias en km alimentan el objetivo/costo; duraciones en minutos alimentan las restricciones temporales y la validación independiente.

Orden obligatorio: C1, C2, C3, P1, P2, P3, P4, P5, P6, P7, P8.

La demanda, inventarios, capacidades y ventanas de tiempo continúan siendo datos simulados. Los puntos P1-P8 son referencias rurales, no coordenadas exactas de predios. OSRM demo es para uso ligero; para uso frecuente implemente caché o un servicio propio.
