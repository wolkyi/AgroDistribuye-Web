# Optimización de rutas de distribución agrícola en Popayán mediante programación entera mixta y aplicación web

**Autores:** Jose Tomas Concha Villamizar y Victor Hugo Fuertes Vidal  
**Institución:** Corporación Universitaria Comfacauca (UNICOMFACAUCA)  
**Programa:** Ingeniería de Sistemas  
**Asignatura:** Investigación de Operaciones  
**Año:** 2026

> **Versión web de consulta del artículo científico AgroDistribuye.** Incluye texto, tablas y las 19 ecuaciones. Las siete figuras gráficas y capturas se conservan en las versiones descargables Word y PDF preparadas para la entrega académica. La aplicación es un prototipo simulado, no una operación empresarial auditada.

**[Ver el repositorio y ejecutar el programa](https://github.com/wolkyi/AgroDistribuye-Web)** · **[Instrucciones de ejecución](https://github.com/wolkyi/AgroDistribuye-Web#ejecutar-en-desarrollo)**

---

Nota de alcance: estudio académico con datos operativos simulados y
matriz vial de OSRM. No se acredita una empresa participante ni se
afirma validación cruzada con Julia.

# Resumen

La planificación de entregas de insumos agrícolas en áreas rurales
requiere coordinar vehículos, inventarios y ventanas horarias. Este
trabajo presenta AgroDistribuye, un prototipo académico de apoyo a la
decisión para el entorno de Popayán, Cauca. Se formuló un problema de
ruteo de vehículos con múltiples depósitos y ventanas de tiempo mediante
programación entera mixta, implementado en Java con OR-Tools/SCIP y una
aplicación web; las distancias y duraciones proceden de una matriz de
rutas viales de OSRM almacenada localmente. La instancia contiene tres
depósitos candidatos, ocho puntos rurales de referencia, cuatro
productos y seis vehículos en una jornada de 07:00 a 18:00. La demanda
simulada asciende a 36,5 toneladas. En la última ejecución registrada,
el escenario de referencia obtuvo una solución factible cercana al
óptimo, validada por comprobaciones internas, con cinco vehículos,
83,203 kilómetros, costo logístico modelado de 658.257 pesos colombianos
y atención de los ocho puntos. Se evaluaron dos alternativas operativas
y variaciones de demanda, capacidad y costo kilométrico, además de
escenarios con penalizaciones por espera y operación. El aumento de
demanda del 20 % y la reducción de capacidad del 20 % resultaron
infactibles en las ejecuciones registradas. No existe línea base
empresarial verificada, por lo que no se atribuyen ahorros reales. Se
propone utilizar el prototipo para explorar decisiones, condicionado a
validar depósitos, demanda, costos y accesibilidad vial.

Palabras clave: ruteo multidepósito; programación entera mixta; ventanas
de tiempo; logística agrícola; Popayán; apoyo a decisiones.

# Abstract

Planning agricultural input deliveries in rural areas requires
coordinating vehicles, inventory, and delivery time windows. This paper
presents AgroDistribuye, an academic decision-support prototype for
Popayán, Cauca, Colombia. A multi-depot vehicle routing problem with
time windows was formulated as a mixed-integer optimization model and
implemented in Java using OR-Tools/SCIP and a web application. Road
distances and travel times were obtained from a locally stored OSRM
routing matrix. The instance includes three candidate depots, eight
rural reference locations, four product categories, and six vehicles
over a single working day from 7:00 a.m. to 6:00 p.m. Simulated demand
totals 36.5 tonnes. In the latest recorded run, the reference scenario
returned an internally validated feasible near-optimal solution using
five vehicles, traveling 83.203 km, with a modeled logistics cost of COP
658,257 and all eight locations served. Two operational alternatives,
sensitivity experiments on demand, capacity, and distance costs, and
scenarios with time penalties were also evaluated. Increasing demand by
20% and reducing vehicle capacity by 20% produced infeasible instances
in the recorded runs. Because no verified organizational baseline is
available, no real-world savings are claimed. The prototype is
recommended for exploratory decision support, subject to validation of
depot operations, demand, cost assumptions, and road access.

Keywords: multi-depot routing; mixed-integer programming; time windows;
agricultural logistics; Popayán; decision support.

# I. Introducción

La distribución rural de fertilizantes, cal agrícola, abonos orgánicos y
semillas requiere asignar pedidos a vehículos con capacidades distintas
y respetar franjas de atención. AgroDistribuye estudia un sistema de
referencia en Popayán con tres posibles puntos de despacho y ocho
centros poblados rurales. No se dispone de registros de operación real,
facturas ni costos históricos auditados: el problema se plantea como
caso aplicado simulado, no como diagnóstico de una empresa específica.

La pregunta de decisión es: ¿qué vehículos, depósitos, secuencias y
horarios permiten cubrir 36,5 t de demanda simulada al menor costo
logístico modelado, respetando capacidad, inventario y ventanas
horarias? Los modelos de ruteo con ventanas temporales son una familia
clásica de problemas combinatorios (Solomon, 1987; Toth y Vigo, 2014).
La extensión a múltiples depósitos añade asignación de vehículos y
restricciones de origen (Montoya-Torres et al., 2015).

## Antecedentes científicos y evolución del modelo

Los modelos de ruteo de vehículos permiten representar la asignación de
clientes, la secuencia de visitas y las limitaciones de la flota. Hajba
et al. (2024) formularon un problema de reabastecimiento mediante
programación lineal entera mixta, con múltiples depósitos, ventanas
horarias y condiciones particulares de los vehículos; recurrieron al
agrupamiento para mejorar su solución en instancias complejas.
Rojas-Cuevas et al. (2025) propusieron otra formulación entera mixta de
rutas con capacidad y un centro de servicio adicional, y contrastaron un
solucionador exacto con una heurística de búsqueda de vecindad variable.
Ambos trabajos respaldan el uso de variables binarias de recorrido y
restricciones de flujo y capacidad.

En distribución agroalimentaria, Fernando et al. (2024) integraron
asignación de recursos, programación de pedidos y ruteo con objetivos de
costo y puntualidad; en su caso de estudio reportaron una reducción de
19 % del costo diario de distribución y un 95 % de entregas puntuales.
Liu et al. (2022) analizaron un problema omnicanal con objetivos
contrapuestos de costo y conveniencia temporal. Estas investigaciones
sustentan la evaluación de escenarios temporales en AgroDistribuye,
aunque sus porcentajes no pueden trasladarse a Popayán, donde las
entradas operativas son simuladas y no existe una línea base empresarial
medida.

El antecedente directo del ejercicio académico inicial fue Lotfalian et
al. (2022), quienes estudiaron la distribución multiproducto de madera
mediante programación lineal continua. Ese modelo decide cantidades
transportadas entre orígenes y destinos, no el orden de visita de los
vehículos. A partir de la revisión bibliográfica y de ese primer
ejercicio, AgroDistribuye evolucionó hacia un problema de ruteo
multidepósito con ventanas horarias (MDVRPTW), que requiere decisiones
discretas de recorrido y horarios de servicio. La formulación
implementada es propia del prototipo y no reproduce literalmente ninguno
de los cinco artículos.

Objetivo general: evaluar una planificación de distribución agrícola
mediante un modelo multidepósito con ventanas de tiempo y una aplicación
web de apoyo a decisiones. Objetivos específicos: caracterizar datos y
supuestos; formular el modelo; implementarlo y comprobar restricciones;
desarrollar la interfaz; comparar escenarios operativos y realizar
sensibilidad paramétrica.

# II. Métodos

## 2.1 Sistema, proceso y supuestos

Se estudia una jornada determinística de 07:00 a 18:00. La red incluye
C1 Alfagro Distribuciones, C2 Grupo Surticampo SAS y C3 Conutrir–Los
Pinos como depósitos candidatos; P1 Cajete, P2 Calibío, P3 Julumito, P4
Pueblillo, P5 Puelenje, P6 Río Blanco, P7 Vereda de Torres y P8 La
Cabuyera como referencias de entrega. Los nombres de comercios no
prueban disponibilidad de bodegas, inventario o aval. Las coordenadas
rurales son centros de referencia, no fincas.

La figura 1 representa el flujo logístico desde las categorías de
insumos hasta los puntos rurales de referencia; no implica relaciones
comerciales verificadas entre los depósitos candidatos.

*[Figura disponible en el artículo PDF y Word.]*

Figura 1. Red conceptual del sistema de distribución simulado. Fuente:
elaboración propia a partir de la instancia AgroDistribuye.

Se supone demanda conocida, inventario inicial disponible, carga en un
único depósito por vehículo, servicio indivisible por cliente, tiempos
de viaje determinísticos y retorno al depósito de origen. No se modelan
tráfico en vivo, cierres de vías, estacionalidad ni localización exacta
de predios. La línea base es el escenario A del modelo, no un plan
empresarial observado.

## 2.2 Datos y tratamiento

Los datos de demanda, stock, costos, flota y ventanas se tomaron de
data/ultima-ejecucion-v8.json y se declaran simulados. Las distancias y
duraciones provienen de OSRM sobre OpenStreetMap, guardadas como
instantánea local; las coordenadas de los depósitos fueron
proporcionadas para el proyecto y no verificadas en terreno. La matriz
es dirigida: ida y regreso pueden diferir.

| Parámetro                        | Valor / unidad                | Procedencia                   |
|----------------------------------|-------------------------------|-------------------------------|
| Depósitos / clientes / productos | 3 / 8 / 4                     | Configuración académica       |
| Vehículos                        | 6: tres de 5 t y tres de 10 t | Simulado                      |
| Demanda total                    | 36,5 t                        | Simulado                      |
| Capacidad depósitos              | 20, 25 y 18 t                 | Simulado                      |
| Jornada                          | 07:00–18:00                   | Supuesto                      |
| Costos fijos por vehículo        | \$70.000 o \$110.000 COP      | Simulado                      |
| Costos variables                 | \$1.800 o \$2.500 COP/km      | Simulado                      |
| Matriz vial                      | 11 × 11 dirigida              | OSRM / OSM, instantánea local |

Tabla 1. Parámetros principales de la instancia. Fuente: última
ejecución incluida en el ZIP.

## 2.3 Formulación de optimización

Trazabilidad de la formulación. El proyecto previo incluía un ejercicio
de transporte por cantidades inspirado en Lotfalian et al. (2022) y
desarrollado en Julia/JuMP y Java/ojAlgo. La aplicación actual resuelve
un problema distinto: determina arcos, asignaciones, depósitos de salida
y tiempos mediante un modelo entero mixto en Java/OR-Tools con SCIP. Por
ello, los resultados anteriores de Julia no constituyen validación
cruzada del MDVRPTW; para ese requisito sería necesaria otra
implementación del mismo modelo con idénticas entradas y restricciones.

El encabezado «Validación estadística de distribuciones (Input
Analyzer)» de la guía contiene en realidad instrucciones de formulación
matemática. No se ajustan distribuciones probabilísticas: el modelo
implementado es determinístico.

| Conjunto | Descripción            | Cardinalidad |
|----------|------------------------|--------------|
| D        | Depósitos candidatos   | 3            |
| P        | Clientes rurales       | 8            |
| V        | Vehículos heterogéneos | 6            |
| K        | Tipos de producto      | 4            |
| N        | Depósitos y clientes   | 11           |

Tabla 2. Conjuntos de la instancia. Fuente: configuración del modelo.

Variables de decisión y parámetros. El modelo crea variables por pareja
depósito–vehículo (d,v), incluso cuando no se utiliza la pareja. Las
tablas 3 y 4 presentan la notación empleada en la formulación, fiel a
las estructuras de datos de AgroDistribuyeMdvRPTW.java.

Tabla 3. Variables de decisión y dominio. Fuente: código fuente del
motor Java.

| **Símbolo** | **Tipo / dominio** | **Significado y unidad**                   |
|-------------|--------------------|--------------------------------------------|
| z(d,v)      | Binaria {0,1}      | Vehículo v utilizado desde depósito d      |
| y(d,v,i)    | Binaria {0,1}      | Vehículo v atiende al productor i desde d  |
| x(d,v,i,j)  | Binaria {0,1}      | Vehículo v utiliza arco dirigido i→j en d  |
| t(d,v,i)    | Continua, min      | Inicio de servicio al productor i          |
| s(d,v)      | Continua, min      | Hora de salida efectiva del depósito       |
| wD(d,v)     | Continua ≥ 0, min  | Espera antes de la salida desde 07:00      |
| wP(d,v,i)   | Continua ≥ 0, min  | Espera modelada antes del servicio i       |
| o(d,v)      | Continua ≥ 0, min  | Tiempo de operación desde salida a regreso |

Tabla 4. Parámetros y unidades del modelo. Fuente: última ejecución y
código Java; valores simulados salvo matriz OSRM.

| **Parámetro**    | **Unidad**  | **Valor / definición**                             |
|------------------|-------------|----------------------------------------------------|
| q(i,k); q̄(i)     | t           | Pedido por producto; total por cliente (3,7–5,0 t) |
| Q(v)             | t           | 5 o 10 por vehículo                                |
| S(d,k)           | t           | Stock por depósito/producto: 1,5–7,0               |
| F(v); c(v)       | COP; COP/km | 70.000 o 110.000; 1.800 o 2.500                    |
| Lᵈ(i,j); τᵈ(i,j) | km; min     | Distancia y duración dirigidas, matriz OSRM        |
| a(i), b(i); h(i) | min         | Ventana \[480,1020\]; servicio 30 o 40             |
| T₀; T_f; M       | min         | 420 (07:00); 1080 (18:00); 2000                    |
| δ(i)             | 0 o 1       | 1 si la demanda total del productor es positiva    |
| α, β, γ          | COP/min     | B: α=50,100,300 y β=200; C: γ=50,100,200           |
| A_d, N_d         | conjuntos   | 72 arcos dirigidos y 9 nodos locales por depósito  |

Función objetivo. Para A se minimizan costos fijos de vehículos y costos
por kilómetro (1). B agrega penalizaciones simuladas por esperas (2) y C
penaliza la duración de la operación (3). El valor de Z_B o Z_C es un
objetivo en COP-equivalentes, no un costo empresarial observado.

**Ecuación (1)**

$$\min Z_{A} = \sum_{d \in D}^{}{\sum_{v \in V}^{}F_{v}}z_{dv} + \sum_{d \in D}^{}{\sum_{v \in V}^{}{\sum_{(i,j) \in A_{d}}^{}c_{v}}}L_{ij}^{d}x_{dvij}$$

|-----------------------------------------------------------------------------------------------------------------------------------------------------------|-------|

**Ecuación (2)**

$$Z_{B} = Z_{A} + \alpha\sum_{d \in D}^{}{\sum_{v \in V}^{}w_{dv}^{D}} + \beta\sum_{d \in D}^{}{\sum_{v \in V}^{}{\sum_{i \in P}^{}w_{dvi}^{P}}}$$

|----------------------------------------------------------------------------------------------------------------------------------------------------|-------|

**Ecuación (3)**

$$Z_{C} = Z_{A} + \gamma\sum_{d \in D}^{}{\sum_{v \in V}^{}o_{dv}}$$

|----------------------------------------------------------------------|-------|

Restricciones de asignación, flujo, capacidad e inventario. Cada
productor con demanda positiva debe ser atendido una vez (4); los
vehículos físicos se activan en un solo depósito y solo visitan clientes
si están activos (5). La salida, el retorno y la conservación de flujo
aparecen en (6) y (7); las capacidades y existencias se imponen en (8) y
(9).

**Ecuación (4)**

$$\sum_{d \in D}^{}{\sum_{v \in V}^{}y_{dvi}} = \delta_{i},\quad\forall i \in P$$

|-----------------------------------------------------------------------------------|-------|

**Ecuación (5)**

$$\sum_{d \in D}^{}z_{dv} \leq 1,\quad y_{dvi} \leq z_{dv}$$

|--------------------------------------------------------------|-------|

**Ecuación (6)**

$$\sum_{j \in P}^{}x_{dv0j} = \sum_{i \in P}^{}x_{dvi0} = z_{dv}$$

|--------------------------------------------------------------------|-------|

**Ecuación (7)**

$$\sum_{j \neq i}^{}x_{dvji} = \sum_{j \neq i}^{}x_{dvij} = y_{dvi}$$

|-----------------------------------------------------------------------|-------|

**Ecuación (8)**

$$\sum_{i \in P}^{}{\bar{q}}_{i}y_{dvi} \leq Q_{v}z_{dv}$$

|------------------------------------------------------------|-------|

**Ecuación (9)**

$$\sum_{v \in V}^{}{\sum_{i \in P}^{}q_{ik}}y_{dvi} \leq S_{dk}$$

|-------------------------------------------------------------------|-------|

Restricciones temporales. Se impone llegada al primer productor (10),
continuidad entre productores (11), retorno antes del cierre (12),
servicio dentro de la ventana (13), espera previa al despacho (14–15),
cotas inferiores de espera en productores (16–17), duración de operación
(18) y dominios (19). Las expresiones (10)–(12) y (16)–(18) se aplican a
los índices y arcos correspondientes.

**Ecuación (10)**

$$t_{dvi} \geq s_{dv} + \tau_{0i}^{d} - M\left( 1 - x_{dv0i} \right)$$

|------------------------------------------------------------------------|--------|

**Ecuación (11)**

$$t_{dvj} \geq t_{dvi} + h_{i} + \tau_{ij}^{d} - M\left( 1 - x_{dvij} \right)$$

|---------------------------------------------------------------------------------|--------|

**Ecuación (12)**

$$t_{dvi} + h_{i} + \tau_{i0}^{d} \leq T_{f} + M\left( 1 - x_{dvi0} \right)$$

|-------------------------------------------------------------------------------|--------|

**Ecuación (13)**

$$a_{i} \leq t_{dvi} \leq b_{i} - h_{i},\quad T_{0} \leq s_{dv} \leq T_{f}$$

|------------------------------------------------------------------------------|--------|

**Ecuación (14)**

$$w_{dv}^{D} = s_{dv} - T_{0}$$

|---------------------------------|--------|

**Ecuación (15)**

$$s_{dv} \leq T_{0} + \left( T_{f} - T_{0} \right)z_{dv},\quad w_{dv}^{D} \leq \left( T_{f} - T_{0} \right)z_{dv}$$

|---------------------------------------------------------------------------------------------------------------------|--------|

**Ecuación (16)**

$$w_{dvi}^{P} \geq t_{dvi} - s_{dv} - \tau_{0i}^{d} - M\left( 1 - x_{dv0i} \right)$$

|--------------------------------------------------------------------------------------|--------|

**Ecuación (17)**

$$w_{dvj}^{P} \geq t_{dvj} - t_{dvi} - h_{i} - \tau_{ij}^{d} - M\left( 1 - x_{dvij} \right)$$

|-----------------------------------------------------------------------------------------------|--------|

**Ecuación (18)**

$$o_{dv} \geq t_{dvi} + h_{i} + \tau_{i0}^{d} - s_{dv} - M\left( 1 - x_{dvi0} \right)$$

|-----------------------------------------------------------------------------------------|--------|

**Ecuación (19)**

$$z_{dv},y_{dvi},x_{dvij} \in \{ 0,1\};\quad t,s,w^{D},w^{P},o \geq 0$$

|-------------------------------------------------------------------------|--------|

En estas ecuaciones, el nodo 0 representa el depósito de la pareja
(d,v), N_d={0}∪P y A_d excluye los bucles. Se define q̄(i)=Σ_k q(i,k) y
δ(i)=1 solo si q̄(i)\>0. La restricción (13) usa el límite superior
b(i)−h(i): el servicio debe terminar dentro de la ventana. La salida se
fija en 07:00 en A y es flexible en B y C. Las esperas de productor y la
duración se imponen mediante cotas inferiores dentro del MILP; los
valores de tiempo presentados al usuario se reconstruyen después a
partir de la secuencia de la ruta. El modelo se clasifica como
programación entera mixta (MILP).

## 2.4 Implementación y verificación

La aplicación utiliza Java, Spring Boot, OR-Tools 9.12.4544 con SCIP,
HTML/CSS/JavaScript y Leaflet; la matriz vial se consulta a OSRM o se
carga de instantáneas locales. La última ejecución guardada informa Java
26.0.1 y 12 procesadores. Para cada escenario se configura un límite de
30.000 ms y brecha MIP objetivo de 1 %. El tiempo total de la solicitud
puede superar ese límite porque incluye varios escenarios, consultas y
preparación de resultados. El escenario A reportó SCIP 9.2.0 \[LP
solver: Glop 9.12\].

La carpeta target/surefire-reports registra 7 pruebas automatizadas
completadas sin fallos (6 de integración y 1 de catálogo), entre ellas
demanda cero con costo cero, rechazo de datos inválidos, inventario
insuficiente como caso infactible, aislamiento de datos, ejecución
desconectada de escenarios y control de trabajos asíncronos. Estas
pruebas y la validación independiente de rutas respaldan coherencia
interna, pero no constituyen comparación Julia–otra herramienta ni
validación empresarial. No se acredita en el ZIP una implementación
Julia/JuMP; la exigencia de la guía debe confirmarse por separado con el
docente.

La tabla 5 resume las comprobaciones efectuadas mediante un recálculo
independiente del archivo de salida. Se compararon pedidos, paradas,
capacidad, existencias y la matriz vial guardada con las rutas del
escenario A. No se volvió a ejecutar el solucionador: la evidencia
corresponde a la instantánea del 7 de noviembre de 2026.

Tabla 5. Comprobaciones de la solución del escenario A. Fuente:
elaboración propia mediante recálculo de data/ultima-ejecucion-v8.json y
contraste con AgroDistribuyeMdvRPTW.java.

| **Control**                  | **Evidencia observada**                                                                                           | **Resultado**       |
|------------------------------|-------------------------------------------------------------------------------------------------------------------|---------------------|
| **Atención y demanda**       | P1–P8 aparecen una vez cada uno; 36,5 t entregadas = 36,5 t solicitadas.                                          | Cumple              |
| **Capacidad por vehículo**   | Cinco rutas: 4,6/5; 8,9/10; 3,7/5; 9,9/10 y 9,4/10 t. Uso máximo: 99 %.                                           | Cumple              |
| **Inventario por producto**  | Las 12 combinaciones depósito–producto cumplen uso ≤ existencias. Menor saldo: 0,2 t de semillas en Alfagro.      | Cumple              |
| **Depósito y continuidad**   | Cinco circuitos completos: cada vehículo sale de un depósito y retorna al mismo, sin repetir productor.           | Cumple              |
| **Ventanas de servicio**     | Los ocho servicios comienzan y terminan dentro de las ventanas simuladas de sus productores.                      | Cumple              |
| **Jornada de reparto**       | Cinco salidas a las 07:00; último regreso a las 17:06, antes del límite de las 18:00.                             | Cumple              |
| **Desglose de tiempos**      | 125,61 min conduciendo + 260,00 min servicio + 1.440,91 min espera = 1.826,52 min de operación agregada.          | Cumple              |
| **Distancia y costo**        | La matriz dirigida de 11 × 11 reproduce 83,203 km y 125,61 min de viaje; la suma de costos reproduce COP 658.257. | Cumple              |
| **Certificación del óptimo** | Estado FEASIBLE_NEAR_OPTIMAL; cota inferior COP 654.159,28 y brecha relativa 0,6225 %.                            | No certifica óptimo |

Evidencia metodológica adicional. (1) Las unidades de cada ecuación
corresponden a toneladas, kilómetros, minutos y COP, según el término.
(2) Los reportes de JUnit documentan demanda cero con costo nulo e
inventario insuficiente con estado infactible; en total, siete pruebas
terminaron sin fallos. (3) La coincidencia con Julia/JuMP u otro
solucionador independiente no está acreditada. (4) La solución respeta
la lógica programada de reparto, pero aún no ha sido validada por una
organización operadora real. Las comprobaciones de la tabla 5 verifican
factibilidad y consistencia, no optimalidad global.

## 2.5 Alternativas y sensibilidad

| Tipo           | Cambio frente a A                        | Propósito                      |
|----------------|------------------------------------------|--------------------------------|
| Vehículo extra | Agregar vehículo de 5 t                  | Examinar flexibilidad de flota |
| Capacidad      | Aumentar 20 % capacidades con sobrecosto | Examinar inversión en flota    |
| Demanda        | −20 % y +20 %                            | Sensibilidad                   |
| Capacidad      | −20 % y +20 %                            | Sensibilidad                   |
| Costo por km   | −20 % y +20 %                            | Sensibilidad                   |
| Tiempo         | Penalización espera o duración           | Analizar horarios              |

Tabla 6. Diseño de experimentos. Fuente: registro de ejecución y código.

## 2.6 Aplicación web

La arquitectura es: formulario editable e importación CSV/JSON →
validación de datos → servicio Spring Boot → modelo OR-Tools/SCIP →
comprobación independiente → resultados, mapa y exportación. La interfaz
ofrece pestañas de resumen, datos, ejecución, resultados y mapa; permite
modificar parámetros, comparar escenarios y utilizar instantáneas sin
conexión. El comportamiento ante datos inválidos y estados infactibles
debe probarse en la sustentación.

La figura 2 sintetiza la arquitectura de apoyo a la decisión y las
entradas de la matriz de viaje. La solución y su auditoría se presentan
sin exigir conocimientos de programación al usuario final.

*[Figura disponible en el artículo PDF y Word.]*

Figura 2. Arquitectura funcional de AgroDistribuye. Fuente: elaboración
propia con base en el código de la versión v8.

# III. Resultados

## 3.1 Tamaño de instancia

La instancia base contiene 3 depósitos, 8 clientes, 4 productos, 6
vehículos y matriz vial dirigida de 11×11 celdas (121 entradas). La
última ejecución registra 1.800 variables: 1.458 binarias —18 de
activación, 144 de visita y 1.296 de arco— y 342 continuas —18 de
salida, 18 de espera en depósito, 18 de duración, 144 de inicio de
servicio y 144 de espera en productores—. Se generaron 3.158
restricciones lineales. Estas cantidades son observaciones del
solucionador en el JSON archivado, no estimaciones teóricas.

## 3.2 Escenario de referencia

La ejecución del 7 de noviembre de 2026 registró estado
FEASIBLE_NEAR_OPTIMAL (factible cercana al óptimo), validación interna
positiva, costo de COP 658.257, 83,20 km, 5 vehículos y 8 puntos
atendidos; tiempo de solución 5,87 s. El estado no demuestra optimalidad
global. Las asignaciones por vehículo de esta ejecución se muestran en
la tabla 7.

| Depósito               | Vehículo   | Paradas | Carga (t) | Km    | Costo (COP) |
|------------------------|------------|---------|-----------|-------|-------------|
| Alfagro Distribuciones | V3-Pequeno | P6      | 4,60      | 21,06 | 107.904     |
| Alfagro Distribuciones | V6-Grande  | P3, P1  | 8,90      | 25,29 | 173.222     |
| Grupo Surticampo SAS   | V2-Pequeno | P4      | 3,70      | 7,16  | 82.883      |
| Grupo Surticampo SAS   | V4-Grande  | P7, P5  | 9,90      | 12,58 | 141.452     |
| Conutrir - Los Pinos   | V5-Grande  | P2, P8  | 9,40      | 17,12 | 152.795     |

Tabla 7. Rutas de la última ejecución. Fuente:
data/ultima-ejecucion-v8.json.

Como se resume en la tabla 5, la auditoría de los datos guardados
reprodujo la asignación única de los ocho productores, 36,5 t atendidas,
12 balances de inventario y cinco circuitos sin sobrecarga. Las rutas
acumularon 125,61 min de conducción, 260 min de servicio y 1.440,91 min
de espera en productores, equivalentes a 1.826,52 min de operación
agregada. Estos tiempos suman la actividad de los vehículos y no
constituyen la duración de una sola jornada.

## 3.3 Comparación entre herramientas

| Herramienta              | Solver    | Objetivo (COP) | Tiempo        | Estado                |
|--------------------------|-----------|----------------|---------------|-----------------------|
| Java / OR-Tools          | SCIP      | 658.257        | 5,87 s        | FEASIBLE_NEAR_OPTIMAL |
| Julia / JuMP             | Pendiente | No disponible  | No disponible | Sin ejecutar          |
| Herramienta de contraste | Pendiente | No disponible  | No disponible | Sin ejecutar          |

Tabla 8. Estado de verificación entre herramientas; no existe
coincidencia acreditada.

## 3.4 Alternativas y escenarios temporales

| Escenario         | Estado           | Costo COP | Objetivo COP | Km    |
|-------------------|------------------|-----------|--------------|-------|
| A BASELINE        | Factible cercana | 658.257   | 658.257      | 83,20 |
| B DEPOT 50        | Factible         | 679.430   | 722.843      | 98,06 |
| B DEPOT 100       | Factible         | 666.116   | 770.636      | 89,03 |
| B DEPOT 300       | Factible         | 680.460   | 853.992      | 98,47 |
| C OPERATION 50    | Factible cercana | 658.257   | 683.078      | 83,20 |
| C OPERATION 100   | Factible         | 658.257   | 707.900      | 83,20 |
| C OPERATION 200   | Factible         | 671.571   | 761.837      | 92,23 |
| ALT EXTRA VEHICLE | Óptimo           | 658.257   | 658.257      | 83,20 |
| ALT CAPACITY      | Factible cercana | 733.257   | 733.257      | 83,20 |

Tabla 9. Comparación de escenarios. Fuente: última ejecución; las
funciones objetivo difieren entre familias.

Agregar un vehículo mantuvo el costo base registrado en COP 658.257;
aumentar capacidades con el sobrecosto simulado elevó el costo a COP
733.257 (+11,39 %). No se demuestra una reducción respecto a una
operación empresarial real. Los objetivos temporales no son directamente
comparables como costos logísticos porque incorporan penalizaciones
distintas.

La figura 3 muestra los costos logísticos observados para el escenario A
y las dos alternativas de flota. En la alternativa de ampliación de
capacidad se incluye el recargo fijo simulado de COP 15.000 por cada
vehículo efectivamente utilizado. Los estados de solución son distintos:
escenario A factible cercano al óptimo, vehículo adicional óptimo
reportado y ampliación de capacidad factible cercana al óptimo.

*[Figura disponible en el artículo PDF y Word.]*

Figura 3. Comparación del costo modelado para decisiones alternativas de
flota. Fuente: elaboración propia a partir de
data/ultima-ejecucion-v8.json.

La figura 4 separa la espera antes de despachar desde el depósito y la
espera ante los productores. En A se fija la salida a las 07:00; B y C
permiten cambiarla con penalizaciones diferentes. La figura no equipara
los valores de sus funciones objetivo ni representa tiempos de espera de
clientes reales.

*[Figura disponible en el artículo PDF y Word.]*

Figura 4. Espera acumulada por localización, en tres escenarios
temporales ilustrativos. Fuente: elaboración propia a partir del
registro de ejecución v8.

## 3.5 Sensibilidad paramétrica

| Parámetro | Factor | Estado                | Costo COP                   |
|-----------|--------|-----------------------|-----------------------------|
| demand    | 0,80   | FEASIBLE_NEAR_OPTIMAL | 625.615                     |
| demand    | 1,20   | INFEASIBLE            | No existe solución factible |
| capacity  | 0,80   | INFEASIBLE            | No existe solución factible |
| capacity  | 1,20   | FEASIBLE_NEAR_OPTIMAL | 658.257                     |
| costPerKm | 0,80   | FEASIBLE_NEAR_OPTIMAL | 620.606                     |
| costPerKm | 1,20   | FEASIBLE_NEAR_OPTIMAL | 695.908                     |

Tabla 10. Sensibilidad ±20 %. Fuente: ejecución registrada.

La demanda +20 % y la capacidad −20 % resultaron infactibles en la
ejecución almacenada. Dado que varias corridas terminan como factibles
sin certificación de óptimo, sus diferencias de costo son resultados
observados del solver, no curvas exactas de valor óptimo.

La figura 5 visualiza la sensibilidad de la tabla 10. Los escenarios
señalados como infactibles carecen de costo de solución y por ello no se
grafican con valor cero. Los puntos representan valores obtenidos en
ejecuciones almacenadas, algunos sin certificado de optimalidad global.

*[Figura disponible en el artículo PDF y Word.]*

Figura 5. Sensibilidad del costo simulado ante variaciones individuales
de demanda, capacidad y costo kilométrico. Fuente: elaboración propia a
partir de data/ultima-ejecucion-v8.json.

## 3.6 Evidencia de la aplicación

*[Figura disponible en el artículo PDF y Word.]*

Las figuras 6 y 7 muestran las pantallas archivadas de la aplicación:
comparación de escenarios y visualización geográfica de recorridos.
Constituyen evidencias de la interfaz, no pruebas de funcionamiento en
tiempo real.

Figura 6. Vista del prototipo: comparación de escenarios. Fuente:
captura incluida en el ZIP del proyecto v8.

*[Figura disponible en el artículo PDF y Word.]*

Figura 7. Vista del prototipo: mapa de rutas. Fuente: captura incluida
en el ZIP del proyecto v8.

Material complementario: [repositorio de código](https://github.com/wolkyi/AgroDistribuye-Web); [instrucciones para ejecutar la aplicación](https://github.com/wolkyi/AgroDistribuye-Web#ejecutar-en-desarrollo). No existe un enlace público verificado a una aplicación desplegada ni un video de demostración.

# IV. Discusión

La planificación de referencia atiende los ocho puntos con cinco de seis
vehículos y 36,5 t de demanda. Las restricciones temporales generan
esperas acumuladas elevadas cuando la salida está fijada a las 07:00. La
programación flexible puede trasladar espera al depósito y reducir
espera en destino, aunque los experimentos B y C modifican la función
objetivo y no representan ahorros empresariales observados.

Los antecedentes permiten interpretar el aporte, pero no establecer
ahorros empresariales comparables. Fernando et al. (2024) trabajaron con
una cadena minorista de gran escala y reportaron mejoras frente a una
operación observada; Rojas-Cuevas et al. (2025) documentaron un ahorro
de 40 % de combustible en un caso de entregas y devoluciones con centro
de servicio. En AgroDistribuye, por el contrario, el costo de COP
658.257 corresponde a parámetros simulados y no existe una medición
equivalente de la situación actual. Por tanto, se demuestra factibilidad
técnica de la planificación, no un porcentaje de ahorro real.

La dificultad de solución exacta observada por Hajba et al. (2024) y
Rojas-Cuevas et al. (2025) también contextualiza la brecha de
optimalidad de la instancia implementada: la salida
FEASIBLE_NEAR_OPTIMAL no acredita un óptimo global. Las alternativas de
espera y duración utilizadas en el prototipo responden a la tensión
entre costo y tiempo discutida por Fernando et al. (2024) y Liu et al.
(2022), pero representan objetivos ponderados distintos; no deben
interpretarse automáticamente como un frente de Pareto.

Las figuras 3–5 permiten distinguir cambios en recursos, cambios en la
función objetivo y sensibilidad de parámetros. Un vehículo adicional no
alteró el costo observado de la ruta base; incrementar capacidad en un
20 % con un recargo por vehículo elevó el costo de COP 658.257 a COP
733.257. La alternativa de espera B (50 COP/min en depósito) trasladó la
espera fuera de los productores para esa solución registrada, mientras
que C (50 COP/min de operación) redujo el tiempo de operación agregado
de 1.826,52 a 496,43 min, a costa de 1.327,85 min de espera previa en
depósito. Estas diferencias reflejan distintas prioridades y horarios
del modelo, no ahorros empíricos.

La sensibilidad muestra fragilidad frente a un aumento del 20 % de la
demanda o una reducción del 20 % de la capacidad, bajo los inventarios y
flota simulados. El vehículo extra no redujo el costo registrado; la
ampliación de capacidad con recargo lo aumentó. Antes de adoptar una
alternativa es indispensable estimar costos reales, comprobar la
disponibilidad de vehículos y contrastar un plan histórico de igual
alcance.

La aplicación aporta edición de entradas, ejecución, mapas, comparación
y exportación sin programar. Su utilidad gerencial depende de datos
confiables, capacitación, revisión de accesibilidad rural y
mantenimiento de la matriz vial. Las limitaciones centrales son datos
simulados, depósitos no verificados, puntos rurales aproximados,
ausencia de tráfico en tiempo real, una sola jornada y ausencia de
validación cruzada Julia/contraste y aval institucional.

# V. Conclusiones

Se construyó un prototipo funcional de ruteo multidepósito con ventanas
horarias que permite analizar una jornada agrícola simulada en Popayán.
En la ejecución conservada se obtuvo una solución factible cercana al
óptimo, con cinco vehículos, 83,203 km y COP 658.257 de costo logístico
modelado, para ocho puntos y 36,5 t.

La sensibilidad registrada revela infactibilidad ante +20 % de demanda y
−20 % de capacidad; la adición de un vehículo no modificó el costo base
observado. Se recomienda al responsable futuro de logística usar la
aplicación en una fase piloto de cuatro semanas, después de validar
demanda, costos, depósitos y direcciones reales; no se proyecta ahorro
monetario sin línea base comprobada.

El prototipo Java/OR-Tools es la herramienta implementada; no puede
recomendarse una herramienta Julia frente a otra sin completar la
validación cruzada exigida. Trabajos futuros: JuMP y contraste
independiente, pruebas extremas, datos empresariales reales, análisis de
incertidumbre, aforos viales y publicación del repositorio.

# Referencias (APA 7.ª edición)

Dantzig, G. B., & Ramser, J. H. (1959). The truck dispatching problem.
Management Science, 6(1), 80–91. https://doi.org/10.1287/mnsc.6.1.80

Fernando, W. M., Thibbotuwawa, A., Perera, H. N., Nielsen, P., & Kilic,
D. K. (2024). An integrated vehicle routing model to optimize
agricultural products distribution in retail chains. Cleaner Logistics
and Supply Chain, 10, Article 100137.
https://doi.org/10.1016/j.clscn.2023.100137

Google. (s. f.). OR-Tools documentation.
https://developers.google.com/optimization

Hajba, T., Horváth, Z., Heitz, D., & Psenák, B. (2024). A MILP approach
combined with clustering to solve a special petrol station replenishment
problem. Central European Journal of Operations Research, 32(1), 95–107.
https://doi.org/10.1007/s10100-023-00849-1

Hillier, F. S., & Lieberman, G. J. (2021). Introduction to operations
research (11th ed.). McGraw-Hill.

JuMP developers. (s. f.). JuMP documentation.
https://jump.dev/JuMP.jl/stable/

Liu, P., Hendalianpour, A., Feylizadeh, M., & Pedrycz, W. (2022).
Mathematical modeling of vehicle routing problem in omni-channel
retailing. Applied Soft Computing, 131, Article 109791.
https://doi.org/10.1016/j.asoc.2022.109791

Lotfalian, M., Peyrov, S., Adeli, K., & Pentek, T. (2022). Determination
of optimal distribution and transportation network: Wood transportation
in Iran. Croatian Journal of Forest Engineering, 43(2), 313–323.
https://doi.org/10.5552/crojfe.2022.1779

Montoya-Torres, J. R., Franco, J. L., Isaza, S. N., Jiménez, H. F., &
Herazo-Padilla, N. (2015). A literature review on the vehicle routing
problem with multiple depots. Computers & Industrial Engineering, 79,
115–129.

Open Source Routing Machine. (s. f.). OSRM project.
https://project-osrm.org/

OpenStreetMap contributors. (s. f.). OpenStreetMap.
https://www.openstreetmap.org/

Rojas-Cuevas, I.-D., Olivares-Benitez, E., Ramos, A. S., &
Nucamendi-Guillén, S. (2025). A capacitated vehicle routing model for
distribution and repair with a service center. Logistics, 9(1), Article
28. https://doi.org/10.3390/logistics9010028

SCIP Optimization Suite. (s. f.). SCIP documentation.
https://www.scipopt.org/

Solomon, M. M. (1987). Algorithms for the vehicle routing and scheduling
problems with time window constraints. Operations Research, 35(2),
254–265. https://doi.org/10.1287/opre.35.2.254

Spring. (s. f.). Spring Boot reference documentation.
https://docs.spring.io/spring-boot/

Toth, P., & Vigo, D. (Eds.). (2014). Vehicle routing: Problems, methods,
and applications (2nd ed.). SIAM.

**Nota sobre clasificación de revistas:** Los cinco artículos son de
2022–2025. El cuartil de la revista varía según la base, categoría y
año. Hajba et al. aparece Q3 en SJR 2024, pero Q2 en JCR 2024 y SJR/JCR
2025. Se debe acordar con el docente el criterio bibliométrico requerido
para acreditarlo como Q1/Q2; los demás títulos tienen registros Q1/Q2
consultables. No se presume que esta clasificación evalúe la calidad
individual de cada estudio.

# Nota de uso de inteligencia artificial

Se utilizó asistencia de inteligencia artificial para estructurar y
redactar el artículo, resumir los archivos del proyecto, producir tablas
y diseñar el póster. Los resultados numéricos proceden de los JSON
incluidos en el ZIP y no de ejecuciones nuevas del solver. El equipo
debe revisar, corregir y validar toda afirmación y referencia antes de
entregar.

# Anexo A. Recursos digitales y límites de publicación

- Repositorio de código y datos de ejemplo: https://github.com/wolkyi/AgroDistribuye-Web
- Instrucciones de ejecución local y demostración sin internet: https://github.com/wolkyi/AgroDistribuye-Web#ejecutar-en-desarrollo
- Versión web del artículo: https://github.com/wolkyi/AgroDistribuye-Web/blob/main/docs/articulo-agrodistribuye.md
- La aplicación no está publicada en un servidor de acceso público y no se ha facilitado un enlace de video.
- La implementación equivalente del mismo MDVRPTW en Julia/JuMP y la validación cruzada con otro solucionador siguen sin acreditarse.

El docente autorizó utilizar datos simulados para el proyecto académico. Las cifras económicas no representan ahorros empresariales observados.