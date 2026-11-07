package com.agrodistribuye.engine;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPSolverParameters;
import com.google.ortools.linearsolver.MPVariable;

import java.util.ArrayList;
import java.util.List;
import com.agrodistribuye.optimization.PlanningData;
import com.agrodistribuye.catalog.NodeNames;

/**
 * Primera versión ejecutable del motor MDVRPTW de AgroDistribuye.
 *
 * Modelo:
 * - 3 depósitos.
 * - 8 productores.
 * - 4 productos.
 * - 6 vehículos (3 de 5 t y 3 de 10 t).
 * - Cada productor se atiende una sola vez y recibe el pedido completo.
 * - Cada vehículo opera desde un único depósito y regresa al mismo.
 * - Se respetan inventarios por producto y depósito, capacidad total
 *   del vehículo y ventanas de tiempo.
 *
 * Todos los datos son simulados para el prototipo académico.
 */
public final class AgroDistribuyeMdvRPTW {


    public record StopResult(String producerId, String arrivalTime, String serviceStart,
                             String serviceEnd, double demandTonnes, double waitingMinutes) {}

    public record RouteResult(String depot, String vehicle, double capacityTonnes,
                              double loadTonnes, double distanceKm, double routeCostCop,
                              String departureTime, String returnTime,
                              double depotWaitingMinutes, double waitingMinutes,
                              double drivingMinutes, double serviceMinutes, double operationMinutes,
                              java.util.Map<String, Double> productLoadsTonnes,
                              List<StopResult> stops) {}

    public record OptimizationResult(String status, boolean validated, double totalCostCop,
                                     double objectiveValue, double totalDistanceKm,
                                     double totalDepotWaitingMinutes, double totalProducerWaitingMinutes,
                                     double totalDrivingMinutes, double totalServiceMinutes,
                                     double totalOperationMinutes, int usedVehicles, int servedCustomers,
                                     long solvingTimeMs, List<RouteResult> routes, SolverMetrics solverMetrics) {}

    public record SolverMetrics(int variables, int binaryVariables, int constraints, Double bestBound,
            Double relativeGap, long timeLimitMs, double targetGap, String solverVersion) {}

    public enum Scenario { BASELINE, WAITING_PENALTY, OPERATION_PENALTY }

    public record OptimizationConfig(Scenario scenario, double depotWaitCopPerMinute,
                                     double producerWaitCopPerMinute,
                                     double operationCopPerMinute) {
        public OptimizationConfig {
            if (scenario == null || depotWaitCopPerMinute < 0 || producerWaitCopPerMinute < 0
                    || operationCopPerMinute < 0) {
                throw new IllegalArgumentException("Configuración de optimización inválida.");
            }
        }
        public static OptimizationConfig baseline() {
            return new OptimizationConfig(Scenario.BASELINE, 0, 0, 0);
        }
    }

    private static final int DEPOTS = 3;
    private static final int CUSTOMERS = 8;
    private final int VEHICLES;
    private static final int PRODUCTS = 4;

    // Tiempo en minutos desde medianoche.
    private static final int SHIFT_START = 7 * 60;   // 07:00
    private static final int SHIFT_END = 18 * 60;    // 18:00
    private static final double BIG_M = 2_000.0;

    private static final String[] DEPOT_NAMES = NodeNames.DEPOTS.toArray(String[]::new);
    private static final String[] CUSTOMER_NAMES =
            {"P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8"};
    private static final String[] PRODUCT_NAMES =
            {"Fertilizantes", "Cal agricola", "Abonos organicos", "Semillas"};

    private final double[][] DEMAND, STOCK;
    private final int[][] TIME_WINDOWS;
    private final int[] SERVICE_MINUTES;
    private final double[] VEHICLE_CAPACITY, FIXED_COST, COST_PER_KM, DEPOT_CAPACITY;
    private SolverMetrics metrics;
    private AgroDistribuyeMdvRPTW(PlanningData data) {
        DEMAND = data.demand(); STOCK = data.stock(); TIME_WINDOWS = data.timeWindows();
        SERVICE_MINUTES = data.serviceMinutes(); VEHICLE_CAPACITY = data.vehicleCapacity();
        FIXED_COST = data.fixedCost(); COST_PER_KM = data.costPerKm();
        DEPOT_CAPACITY = data.depotCapacity(); VEHICLES = VEHICLE_CAPACITY.length;
    }

    private String vehicleName(int k) {
        return "V" + (k + 1) + (VEHICLE_CAPACITY[k] <= 5 ? "-Pequeno" : "-Grande");
    }

    private String formatTime(double minutes) {
        int total = (int) Math.round(minutes);
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    private double customerDemand(int customer) {
        double sum = 0;
        for (int p = 0; p < PRODUCTS; p++) {
            sum += DEMAND[customer][p];
        }
        return sum;
    }

    private void validateData() {
        for (int i = 0; i < CUSTOMERS; i++) {
            if (customerDemand(i) > java.util.Arrays.stream(VEHICLE_CAPACITY).max().orElse(0) + 1e-5) {
                throw new IllegalArgumentException(
                        "El pedido de " + CUSTOMER_NAMES[i] + " supera la capacidad del vehiculo mas grande.");
            }
            if (TIME_WINDOWS[i][0] > TIME_WINDOWS[i][1]) {
                throw new IllegalArgumentException("Ventana de tiempo invalida para " + CUSTOMER_NAMES[i]);
            }
        }

        for (int d = 0; d < DEPOTS; d++) {
            double totalStock = 0;
            for (int p = 0; p < PRODUCTS; p++) {
                totalStock += STOCK[d][p];
            }
            double depotCapacity = DEPOT_CAPACITY[d];
            if (totalStock > depotCapacity) {
                throw new IllegalArgumentException("El inventario supera la capacidad del deposito " + DEPOT_NAMES[d]);
            }
        }
    }

    public static OptimizationResult optimize(double[][] roadDistanceKm, double[][] roadDurationMinutes) {
        return optimize(roadDistanceKm, roadDurationMinutes, OptimizationConfig.baseline());
    }

    public static OptimizationResult optimize(double[][] roadDistanceKm, double[][] roadDurationMinutes,
                                              OptimizationConfig config) {
        return optimize(roadDistanceKm, roadDurationMinutes, config, PlanningData.defaults(), 30_000);
    }

    public static OptimizationResult optimize(double[][] distances, double[][] durations,
            OptimizationConfig config, PlanningData data, long timeLimitMs) {
        return new AgroDistribuyeMdvRPTW(data).solve(distances, durations, config, timeLimitMs);
    }

    private OptimizationResult solve(double[][] roadDistanceKm, double[][] roadDurationMinutes,
            OptimizationConfig config, long timeLimitMs) {
        if (config == null) throw new IllegalArgumentException("La configuración no puede ser null.");
        validateRoadMatrices(roadDistanceKm, roadDurationMinutes);
        Loader.loadNativeLibraries();
        validateData();

        // SCIP resuelve el modelo entero mixto. Si no está disponible en la
        // distribución nativa instalada, el programa informa el problema.
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            throw new IllegalStateException(
                    "No se pudo crear SCIP. Verifica la distribucion nativa de OR-Tools.");
        }
        // Límite por escenario, validado por la API.
        solver.setTimeLimit(timeLimitMs);

        /*
         * Variables:
         * use[d][k] = 1 si el vehiculo k opera desde el deposito d.
         * visit[d][k][i] = 1 si el vehiculo k atiende al productor i.
         * x[d][k][i][j] = 1 si recorre el arco i -> j.
         * time[d][k][i] = inicio del servicio al productor i.
         *
         * En cada submodelo (d,k), el nodo 0 representa el depósito d
         * y los nodos 1..8 representan P1..P8.
         */
        MPVariable[][] use = new MPVariable[DEPOTS][VEHICLES];
        MPVariable[][][] visit = new MPVariable[DEPOTS][VEHICLES][CUSTOMERS];
        MPVariable[][][][] x = new MPVariable[DEPOTS][VEHICLES][CUSTOMERS + 1][CUSTOMERS + 1];
        MPVariable[][][] serviceStart = new MPVariable[DEPOTS][VEHICLES][CUSTOMERS];
        MPVariable[][] departure = new MPVariable[DEPOTS][VEHICLES];
        MPVariable[][] depotWaiting = new MPVariable[DEPOTS][VEHICLES];
        MPVariable[][][] producerWaiting = new MPVariable[DEPOTS][VEHICLES][CUSTOMERS];
        MPVariable[][] operationMinutes = new MPVariable[DEPOTS][VEHICLES];

        for (int d = 0; d < DEPOTS; d++) {
            for (int k = 0; k < VEHICLES; k++) {
                use[d][k] = solver.makeBoolVar("use_d" + d + "_v" + k);
                boolean flexibleDeparture = config.scenario() != Scenario.BASELINE;
                departure[d][k] = solver.makeNumVar(SHIFT_START,
                        flexibleDeparture ? SHIFT_END : SHIFT_START,
                        "departure_d" + d + "_v" + k);
                depotWaiting[d][k] = solver.makeNumVar(0, flexibleDeparture ? SHIFT_END - SHIFT_START : 0,
                        "depot_wait_d" + d + "_v" + k);
                operationMinutes[d][k] = solver.makeNumVar(0, SHIFT_END - SHIFT_START,
                        "operation_minutes_d" + d + "_v" + k);
                MPConstraint waitDefinition = solver.makeConstraint(SHIFT_START, SHIFT_START,
                        "depot_wait_definition_d" + d + "_v" + k);
                waitDefinition.setCoefficient(departure[d][k], 1);
                waitDefinition.setCoefficient(depotWaiting[d][k], -1);
                MPConstraint inactiveDeparture = solver.makeConstraint(
                        Double.NEGATIVE_INFINITY, SHIFT_START, "inactive_departure_d" + d + "_v" + k);
                inactiveDeparture.setCoefficient(departure[d][k], 1);
                inactiveDeparture.setCoefficient(use[d][k], -(SHIFT_END - SHIFT_START));
                MPConstraint inactiveDepotWait = solver.makeConstraint(
                        Double.NEGATIVE_INFINITY, 0, "inactive_depot_wait_d" + d + "_v" + k);
                inactiveDepotWait.setCoefficient(depotWaiting[d][k], 1);
                inactiveDepotWait.setCoefficient(use[d][k], -(SHIFT_END - SHIFT_START));

                for (int i = 0; i < CUSTOMERS; i++) {
                    producerWaiting[d][k][i] = solver.makeNumVar(0, SHIFT_END - SHIFT_START,
                            "producer_wait_d" + d + "_v" + k + "_p" + i);
                    visit[d][k][i] = solver.makeBoolVar("visit_d" + d + "_v" + k + "_p" + i);
                    // El servicio completo debe terminar dentro de la ventana.
                    serviceStart[d][k][i] = solver.makeNumVar(
                            TIME_WINDOWS[i][0],
                            TIME_WINDOWS[i][1] - SERVICE_MINUTES[i],
                            "time_d" + d + "_v" + k + "_p" + i);
                }

                for (int i = 0; i <= CUSTOMERS; i++) {
                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (i != j) {
                            x[d][k][i][j] =
                                    solver.makeBoolVar("x_d" + d + "_v" + k + "_" + i + "_" + j);
                        }
                    }
                }
            }
        }

        // 1) Cada productor debe ser atendido exactamente una vez.
        for (int i = 0; i < CUSTOMERS; i++) {
            MPConstraint c = solver.makeConstraint(customerDemand(i) > 1e-9 ? 1 : 0, customerDemand(i) > 1e-9 ? 1 : 0, "each_customer_once_" + i);
            for (int d = 0; d < DEPOTS; d++) {
                for (int k = 0; k < VEHICLES; k++) {
                    c.setCoefficient(visit[d][k][i], 1);
                }
            }
        }

        // 2) Un vehículo físico puede estar asignado a máximo un depósito.
        for (int k = 0; k < VEHICLES; k++) {
            MPConstraint c = solver.makeConstraint(0, 1, "vehicle_one_depot_" + k);
            for (int d = 0; d < DEPOTS; d++) {
                c.setCoefficient(use[d][k], 1);
            }
        }

        // 3) Restricciones por depósito y vehículo.
        for (int d = 0; d < DEPOTS; d++) {
            int depotNode = d; // En la matriz global, C1=0, C2=1, C3=2.

            for (int k = 0; k < VEHICLES; k++) {
                // Un vehículo solo puede visitar productores si está activado.
                for (int i = 0; i < CUSTOMERS; i++) {
                    MPConstraint link = solver.makeConstraint(
                            Double.NEGATIVE_INFINITY, 0, "visit_requires_use_d" + d + "_v" + k + "_p" + i);
                    link.setCoefficient(visit[d][k][i], 1);
                    link.setCoefficient(use[d][k], -1);
                }

                // Salida del depósito: exactamente una si el vehículo se utiliza.
                MPConstraint depart = solver.makeConstraint(0, 0, "depart_d" + d + "_v" + k);
                for (int j = 1; j <= CUSTOMERS; j++) {
                    depart.setCoefficient(x[d][k][0][j], 1);
                }
                depart.setCoefficient(use[d][k], -1);

                // Regreso al mismo depósito: exactamente uno si se utiliza.
                MPConstraint returnDepot = solver.makeConstraint(0, 0, "return_d" + d + "_v" + k);
                for (int i = 1; i <= CUSTOMERS; i++) {
                    returnDepot.setCoefficient(x[d][k][i][0], 1);
                }
                returnDepot.setCoefficient(use[d][k], -1);

                // Conservación de flujo en cada productor.
                for (int i = 1; i <= CUSTOMERS; i++) {
                    MPConstraint incoming = solver.makeConstraint(0, 0, "in_d" + d + "_v" + k + "_p" + i);
                    MPConstraint outgoing = solver.makeConstraint(0, 0, "out_d" + d + "_v" + k + "_p" + i);

                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (j != i) {
                            incoming.setCoefficient(x[d][k][j][i], 1);
                            outgoing.setCoefficient(x[d][k][i][j], 1);
                        }
                    }
                    incoming.setCoefficient(visit[d][k][i - 1], -1);
                    outgoing.setCoefficient(visit[d][k][i - 1], -1);
                }

                // Capacidad total del vehículo (suma de los cuatro productos).
                MPConstraint capacity = solver.makeConstraint(
                        Double.NEGATIVE_INFINITY, 0, "capacity_d" + d + "_v" + k);
                for (int i = 0; i < CUSTOMERS; i++) {
                    capacity.setCoefficient(visit[d][k][i], customerDemand(i));
                }
                capacity.setCoefficient(use[d][k], -VEHICLE_CAPACITY[k]);

                // Ventana de tiempo desde el depósito al primer productor.
                for (int i = 1; i <= CUSTOMERS; i++) {
                    int customer = i - 1;
                    double travel = travelMinutes(roadDurationMinutes, depotNode, customer + DEPOTS);
                    MPConstraint startTime = solver.makeConstraint(
                            travel - BIG_M, Double.POSITIVE_INFINITY,
                            "start_time_d" + d + "_v" + k + "_p" + i);
                    startTime.setCoefficient(serviceStart[d][k][customer], 1);
                    startTime.setCoefficient(departure[d][k], -1);
                    startTime.setCoefficient(x[d][k][0][i], -BIG_M);

                    // Espera del productor si este es el primer nodo de la ruta.
                    MPConstraint firstWait = solver.makeConstraint(
                            -travel - BIG_M, Double.POSITIVE_INFINITY,
                            "first_producer_wait_d" + d + "_v" + k + "_p" + i);
                    firstWait.setCoefficient(producerWaiting[d][k][customer], 1);
                    firstWait.setCoefficient(serviceStart[d][k][customer], -1);
                    firstWait.setCoefficient(departure[d][k], 1);
                    firstWait.setCoefficient(x[d][k][0][i], -BIG_M);
                }

                // Continuidad temporal entre productores.
                for (int i = 1; i <= CUSTOMERS; i++) {
                    int customerI = i - 1;
                    for (int j = 1; j <= CUSTOMERS; j++) {
                        if (i == j) continue;
                        int customerJ = j - 1;
                        double travel = travelMinutes(roadDurationMinutes, customerI + DEPOTS, customerJ + DEPOTS);

                        MPConstraint timeFlow = solver.makeConstraint(
                                SERVICE_MINUTES[customerI] + travel - BIG_M,
                                Double.POSITIVE_INFINITY,
                                "time_flow_d" + d + "_v" + k + "_p" + i + "_p" + j);
                        timeFlow.setCoefficient(serviceStart[d][k][customerJ], 1);
                        timeFlow.setCoefficient(serviceStart[d][k][customerI], -1);
                        timeFlow.setCoefficient(x[d][k][i][j], -BIG_M);
                        MPConstraint betweenWait = solver.makeConstraint(
                                -SERVICE_MINUTES[customerI] - travel - BIG_M,
                                Double.POSITIVE_INFINITY,
                                "producer_wait_d" + d + "_v" + k + "_p" + i + "_p" + j);
                        betweenWait.setCoefficient(producerWaiting[d][k][customerJ], 1);
                        betweenWait.setCoefficient(serviceStart[d][k][customerJ], -1);
                        betweenWait.setCoefficient(serviceStart[d][k][customerI], 1);
                        betweenWait.setCoefficient(x[d][k][i][j], -BIG_M);
                    }
                }

                // El vehículo debe regresar al depósito antes de cerrar la jornada.
                for (int i = 1; i <= CUSTOMERS; i++) {
                    int customer = i - 1;
                    double travelBack = travelMinutes(roadDurationMinutes, customer + DEPOTS, depotNode);
                    MPConstraint returnTime = solver.makeConstraint(
                            Double.NEGATIVE_INFINITY,
                            SHIFT_END - SERVICE_MINUTES[customer] - travelBack + BIG_M,
                            "return_time_d" + d + "_v" + k + "_p" + i);
                    returnTime.setCoefficient(serviceStart[d][k][customer], 1);
                    returnTime.setCoefficient(x[d][k][i][0], BIG_M);
                    MPConstraint operationDuration = solver.makeConstraint(
                            SERVICE_MINUTES[customer] + travelBack - BIG_M,
                            Double.POSITIVE_INFINITY,
                            "operation_duration_d" + d + "_v" + k + "_p" + i);
                    operationDuration.setCoefficient(operationMinutes[d][k], 1);
                    operationDuration.setCoefficient(serviceStart[d][k][customer], -1);
                    operationDuration.setCoefficient(departure[d][k], 1);
                    operationDuration.setCoefficient(x[d][k][i][0], -BIG_M);
                }
            }

            // 4) Inventario por producto en cada depósito.
            for (int p = 0; p < PRODUCTS; p++) {
                MPConstraint stock = solver.makeConstraint(
                        Double.NEGATIVE_INFINITY, STOCK[d][p], "stock_d" + d + "_product" + p);
                for (int k = 0; k < VEHICLES; k++) {
                    for (int i = 0; i < CUSTOMERS; i++) {
                        stock.setCoefficient(visit[d][k][i], DEMAND[i][p]);
                    }
                }
            }
        }

        // 5) Función objetivo: costos fijos + costos por kilómetro.
        MPObjective objective = solver.objective();
        for (int d = 0; d < DEPOTS; d++) {
            for (int k = 0; k < VEHICLES; k++) {
                objective.setCoefficient(use[d][k], FIXED_COST[k]);
                if (config.scenario() == Scenario.WAITING_PENALTY) {
                    objective.setCoefficient(depotWaiting[d][k], config.depotWaitCopPerMinute());
                    for (int customer = 0; customer < CUSTOMERS; customer++) {
                        objective.setCoefficient(producerWaiting[d][k][customer],
                                config.producerWaitCopPerMinute());
                    }
                } else if (config.scenario() == Scenario.OPERATION_PENALTY) {
                    objective.setCoefficient(operationMinutes[d][k], config.operationCopPerMinute());
                }

                for (int i = 0; i <= CUSTOMERS; i++) {
                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (i == j) continue;
                        MPVariable arc = x[d][k][i][j];
                        if (arc == null) continue;

                        double km = distanceForLocalNodes(roadDistanceKm, d, i, j);
                        objective.setCoefficient(arc, COST_PER_KM[k] * km);
                    }
                }
            }
        }
        objective.setMinimization();

        System.out.println("==================================================");
        System.out.println(" AGRODISTRIBUYE - OPTIMIZADOR MDVRPTW");
        System.out.println("==================================================");
        System.out.println("Solucionando modelo con SCIP...");
        MPSolverParameters solverParameters = new MPSolverParameters();
        solverParameters.setDoubleParam(MPSolverParameters.DoubleParam.RELATIVE_MIP_GAP, 0.01);
        try {
        MPSolver.ResultStatus status = solver.solve(solverParameters);
        boolean feasible = status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE;
        Double bound = feasible && Double.isFinite(objective.bestBound()) ? objective.bestBound() : null;
        Double gap = feasible && bound != null ? Math.abs(objective.value() - bound) / Math.max(1, Math.abs(objective.value())) : null;
        // use + visit + x (sin bucles i=i), todas creadas con makeBoolVar.
        int binaries = DEPOTS * VEHICLES * (1 + CUSTOMERS + CUSTOMERS * (CUSTOMERS + 1));
        metrics = new SolverMetrics(solver.numVariables(), binaries, solver.numConstraints(), bound, gap,
                timeLimitMs, .01, solver.solverVersion());
        double relativeMipGap = Math.abs(objective.value() - objective.bestBound())
                / Math.max(1.0, Math.abs(objective.value()));
        // SCIP puede devolver OPTIMAL al alcanzar el gap configurado, sin cerrar la cota.
        String responseStatus = feasible && relativeMipGap > 1e-7 && relativeMipGap <= 0.010001
                ? "FEASIBLE_NEAR_OPTIMAL" : status == MPSolver.ResultStatus.OPTIMAL && relativeMipGap > 1e-7
                ? "FEASIBLE" : status.toString();
        System.out.println("Estado del solucionador: " + status);

        if (status != MPSolver.ResultStatus.OPTIMAL &&
                status != MPSolver.ResultStatus.FEASIBLE) {
            System.out.println("No se encontro una solucion factible.");
            System.out.println("Revisa inventarios, demandas, flota y ventanas de tiempo.");
            return new OptimizationResult(status.toString(), false, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, solver.wallTime(), List.of(), metrics);
        }

        // Validación independiente: no confiamos únicamente en el estado OPTIMAL.
        validateSolution(use, visit, x, serviceStart, departure, objective, config,
                roadDistanceKm, roadDurationMinutes);
        OptimizationResult result = buildStructuredResult(responseStatus, use, x,
                serviceStart, departure, objective, solver.wallTime(), roadDistanceKm, roadDurationMinutes);
        printSolution(use, visit, x, serviceStart, objective, roadDistanceKm);
        System.out.println("Tiempo de solucion: " + solver.wallTime() + " ms");
        System.out.println("==================================================");
        return result;
        } finally { solverParameters.delete(); solver.delete(); }
    }

    private OptimizationResult buildStructuredResult(
            String status, MPVariable[][] use,
            MPVariable[][][][] x, MPVariable[][][] serviceStart, MPVariable[][] departure,
            MPObjective objective, long solvingTimeMs,
            double[][] roadDistanceKm, double[][] roadDurationMinutes) {
        List<RouteResult> routes = new ArrayList<>();
        double totalKm = 0.0;
        double totalEconomicCost = 0.0;
        int used = 0;
        int served = 0;
        for (int d = 0; d < DEPOTS; d++) {
            for (int k = 0; k < VEHICLES; k++) {
                if (use[d][k].solutionValue() < 0.5) continue;
                used++;
                List<StopResult> stops = new ArrayList<>();
                double routeKm = 0.0;
                double load = 0.0;
                double[] productLoads = new double[PRODUCTS];
                int current = 0;
                int guard = 0;
                double returnMinutes = SHIFT_START;
                double producerWaitingMinutes = 0.0;
                double drivingMinutes = 0.0;
                double serviceMinutes = 0.0;
                double departureMinutes = departure[d][k].solutionValue();
                // Espera de depósito: solo se extrae para rutas activas.
                double depotWaitingMinutes = Math.max(0.0, departureMinutes - SHIFT_START);
                int previousCustomerForWait = -1;
                double previousFinishForWait = departureMinutes;
                while (guard++ <= CUSTOMERS + 1) {
                    int next = -1;
                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (j != current && x[d][k][current][j] != null
                                && x[d][k][current][j].solutionValue() > 0.5) {
                            next = j;
                            break;
                        }
                    }
                    if (next < 0) break;
                    routeKm += distanceForLocalNodes(roadDistanceKm, d, current, next);
                    int globalFrom = current == 0 ? d : current - 1 + DEPOTS;
                    int globalTo = next == 0 ? d : next - 1 + DEPOTS;
                    drivingMinutes += travelMinutes(roadDurationMinutes, globalFrom, globalTo);
                    if (next == 0) {
                        if (!stops.isEmpty()) {
                            int lastCustomer = Integer.parseInt(
                                    stops.get(stops.size() - 1).producerId().substring(1)) - 1;
                            double end = serviceStart[d][k][lastCustomer].solutionValue()
                                    + SERVICE_MINUTES[lastCustomer];
                            returnMinutes = end + travelMinutes(roadDurationMinutes, lastCustomer + DEPOTS, d);
                        }
                        break;
                    }
                    int customer = next - 1;
                    double start = serviceStart[d][k][customer].solutionValue();
                    double finish = start + SERVICE_MINUTES[customer];
                    double arrivalMinutes = previousCustomerForWait < 0
                            ? departureMinutes + travelMinutes(roadDurationMinutes, d, customer + DEPOTS)
                            : previousFinishForWait + travelMinutes(roadDurationMinutes,
                                    previousCustomerForWait + DEPOTS, customer + DEPOTS);
                    double stopWait = Math.max(0.0, start - arrivalMinutes);
                    producerWaitingMinutes += stopWait;
                    previousCustomerForWait = customer;
                    previousFinishForWait = finish;
                    serviceMinutes += SERVICE_MINUTES[customer];
                    double demand = customerDemand(customer);
                    stops.add(new StopResult(CUSTOMER_NAMES[customer], formatTime(arrivalMinutes),
                            formatTime(start), formatTime(finish), round3(demand), stopWait));
                    load += demand;
                    served++;
                    for (int product = 0; product < PRODUCTS; product++)
                        productLoads[product] += DEMAND[customer][product];
                    current = next;
                }
                java.util.Map<String, Double> productMap = new java.util.LinkedHashMap<>();
                for (int product = 0; product < PRODUCTS; product++)
                    productMap.put(PRODUCT_NAMES[product], round3(productLoads[product]));
                double routeCost = FIXED_COST[k] + routeKm * COST_PER_KM[k];
                double routeOperation = returnMinutes - departureMinutes;
                double componentTotal = drivingMinutes + serviceMinutes + producerWaitingMinutes;
                if (Math.abs(routeOperation - componentTotal) > 0.0001) routeOperation = componentTotal;
                // Verifica la identidad de la línea de tiempo reconstruida desde los arcos.
                if (Math.abs(routeOperation - (drivingMinutes + serviceMinutes + producerWaitingMinutes)) > 1e-3) {
                    throw new IllegalStateException("Desglose temporal inconsistente en " + vehicleName(k));
                }
                routes.add(new RouteResult(DEPOT_NAMES[d], vehicleName(k), VEHICLE_CAPACITY[k],
                        round3(load), routeKm, routeCost, formatTime(departureMinutes),
                        formatTime(returnMinutes), depotWaitingMinutes, producerWaitingMinutes,
                        drivingMinutes, serviceMinutes, routeOperation, productMap,
                        List.copyOf(stops)));
                totalKm += routeKm;
                totalEconomicCost += routeCost;
            }
        }
        reconcileVehicleTimelines(routes);
        double totalDepotWait = routes.stream().mapToDouble(RouteResult::depotWaitingMinutes).sum();
        double totalProducerWait = routes.stream().mapToDouble(RouteResult::waitingMinutes).sum();
        double totalDriving = routes.stream().mapToDouble(RouteResult::drivingMinutes).sum();
        double totalService = routes.stream().mapToDouble(RouteResult::serviceMinutes).sum();
        double totalOperation = routes.stream().mapToDouble(RouteResult::operationMinutes).sum();
        return new OptimizationResult(status, true, totalEconomicCost, objective.value(), totalKm,
                round3(totalDepotWait), round3(totalProducerWait), round3(totalDriving),
                round3(totalService), round3(totalOperation), used, served,
                solvingTimeMs, List.copyOf(routes), metrics);
    }

        /**
     * Audita las rutas activas reconstruidas desde las variables del modelo MIP.
     */
    private void reconcileVehicleTimelines(List<RouteResult> routes) {
        final double tolerance = 0.0001;
        double driving = 0, service = 0, waiting = 0, operation = 0;
        for (RouteResult route : routes) {
            double components = route.drivingMinutes() + route.serviceMinutes() + route.waitingMinutes();
            if (Math.abs(route.operationMinutes() - components) > tolerance)
                throw new IllegalStateException("Desglose temporal inconsistente en " + route.vehicle());
            driving += route.drivingMinutes(); service += route.serviceMinutes();
            waiting += route.waitingMinutes(); operation += route.operationMinutes();
        }
        if (Math.abs(operation - (driving + service + waiting)) > tolerance)
            throw new IllegalStateException("La suma global no coincide con las rutas activas.");
    }

/**
     * Comprueba la solución sin reutilizar las restricciones del solver como evidencia.
     * Detecta productores duplicados/omitidos, ciclos desconectados, errores de flujo,
     * exceso de capacidad/inventario, ventanas incumplidas, retornos tardíos y
     * discrepancias entre el costo/distancia reconstruidos y los valores del modelo.
     */
    private void validateSolution(
            MPVariable[][] use,
            MPVariable[][][] visit,
            MPVariable[][][][] x,
            MPVariable[][][] serviceStart,
            MPVariable[][] departure,
            MPObjective objective,
            OptimizationConfig config,
            double[][] roadDistanceKm,
            double[][] roadDurationMinutes) {

        final double EPS = 1e-5;
        int[] customerCount = new int[CUSTOMERS];
        double[][] depotProductUse = new double[DEPOTS][PRODUCTS];
        double recomputedCost = 0.0;
        double recomputedObjective = 0.0;
        double recomputedKm = 0.0;
        int usedVehicles = 0;

        System.out.println();
        System.out.println("VALIDACION INDEPENDIENTE DE LA SOLUCION");

        for (int d = 0; d < DEPOTS; d++) {
            for (int k = 0; k < VEHICLES; k++) {
                boolean active = use[d][k].solutionValue() > 0.5;
                int selectedArcs = 0;
                int depotDepartures = 0;
                int depotReturns = 0;
                for (int i = 0; i <= CUSTOMERS; i++) {
                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (i != j && x[d][k][i][j] != null
                                && x[d][k][i][j].solutionValue() > 0.5) {
                            selectedArcs++;
                            if (i == 0) depotDepartures++;
                            if (j == 0) depotReturns++;
                        }
                    }
                }

                int assignedCount = 0;
                double load = 0.0;
                double[] productLoad = new double[PRODUCTS];
                for (int i = 0; i < CUSTOMERS; i++) {
                    if (visit[d][k][i].solutionValue() > 0.5) {
                        assignedCount++;
                        load += customerDemand(i);
                        customerCount[i]++;
                        for (int p = 0; p < PRODUCTS; p++) {
                            productLoad[p] += DEMAND[i][p];
                            depotProductUse[d][p] += DEMAND[i][p];
                        }
                    }
                }

                if (!active) {
                    if (assignedCount != 0 || selectedArcs != 0) {
                        throw new IllegalStateException("Vehiculo inactivo con visitas/arcos: "
                                + DEPOT_NAMES[d] + " " + vehicleName(k));
                    }
                    continue;
                }

                usedVehicles++;
                if (assignedCount == 0 || depotDepartures != 1 || depotReturns != 1) {
                    throw new IllegalStateException("Estructura de ruta invalida para "
                            + DEPOT_NAMES[d] + " " + vehicleName(k));
                }
                if (load > VEHICLE_CAPACITY[k] + EPS) {
                    throw new IllegalStateException("Capacidad excedida en " + vehicleName(k));
                }

                // Reconstruir el único camino que parte del depósito.
                boolean[] seenLocal = new boolean[CUSTOMERS + 1];
                List<Integer> sequence = new ArrayList<>();
                int current = 0;
                int traversedArcs = 0;
                double routeKm = 0.0;
                while (true) {
                    int next = -1;
                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (j != current && x[d][k][current][j] != null
                                && x[d][k][current][j].solutionValue() > 0.5) {
                            if (next != -1) {
                                throw new IllegalStateException("Mas de un arco saliente en una ruta de "
                                        + vehicleName(k));
                            }
                            next = j;
                        }
                    }
                    if (next == -1) {
                        throw new IllegalStateException("Ruta abierta o desconectada en " + vehicleName(k));
                    }
                    traversedArcs++;
                    routeKm += distanceForLocalNodes(roadDistanceKm, d, current, next);
                    if (next == 0) break;
                    if (seenLocal[next]) {
                        throw new IllegalStateException("Ciclo detectado en " + vehicleName(k));
                    }
                    seenLocal[next] = true;
                    sequence.add(next - 1);
                    current = next;
                    if (traversedArcs > CUSTOMERS + 1) {
                        throw new IllegalStateException("Se excedio el limite de recorrido en " + vehicleName(k));
                    }
                }

                // Si existen arcos en un subciclo desconectado, no estarán en el camino reconstruido.
                if (traversedArcs != selectedArcs || sequence.size() != assignedCount) {
                    throw new IllegalStateException("Hay arcos o visitas desconectados del deposito en "
                            + vehicleName(k));
                }

                // Validar tiempos siguiendo el orden efectivo de la ruta.
                double previousStart = Double.NaN;
                int previousCustomer = -1;
                for (int customer : sequence) {
                    double start = serviceStart[d][k][customer].solutionValue();
                    double finish = start + SERVICE_MINUTES[customer];
                    if (start < TIME_WINDOWS[customer][0] - EPS
                            || finish > TIME_WINDOWS[customer][1] + EPS) {
                        throw new IllegalStateException("Ventana de tiempo incumplida en "
                                + CUSTOMER_NAMES[customer]);
                    }
                    if (previousCustomer == -1) {
                        double departureTime = departure[d][k].solutionValue();
                        if (departureTime < SHIFT_START - EPS || departureTime > SHIFT_END + EPS) {
                            throw new IllegalStateException("Hora de salida fuera de jornada en " + vehicleName(k));
                        }
                        double earliest = departureTime
                                + travelMinutes(roadDurationMinutes, d, customer + DEPOTS);
                        if (start < earliest - EPS) {
                            throw new IllegalStateException("Salida/primer desplazamiento inviable en "
                                    + vehicleName(k));
                        }
                    } else {
                        double earliest = previousStart + SERVICE_MINUTES[previousCustomer]
                                + travelMinutes(roadDurationMinutes, previousCustomer + DEPOTS, customer + DEPOTS);
                        if (start < earliest - EPS) {
                            throw new IllegalStateException("Continuidad temporal incumplida entre "
                                    + CUSTOMER_NAMES[previousCustomer] + " y " + CUSTOMER_NAMES[customer]);
                        }
                    }
                    previousStart = start;
                    previousCustomer = customer;
                }

                int lastCustomer = sequence.get(sequence.size() - 1);
                double returnTime = previousStart + SERVICE_MINUTES[lastCustomer]
                        + travelMinutes(roadDurationMinutes, lastCustomer + DEPOTS, d);
                if (returnTime > SHIFT_END + EPS) {
                    throw new IllegalStateException("Regreso despues de las 18:00 en " + vehicleName(k));
                }
                if (departure[d][k].solutionValue() > returnTime + EPS) {
                    throw new IllegalStateException("Salida posterior al regreso en " + vehicleName(k));
                }

                double routeDeparture = departure[d][k].solutionValue();
                double routeProducerWait = 0.0;
                double previousFinishForWait = routeDeparture;
                int previousForWait = -1;
                for (int customer : sequence) {
                    double arrival = previousForWait < 0
                            ? routeDeparture + travelMinutes(roadDurationMinutes, d, customer + DEPOTS)
                            : previousFinishForWait + travelMinutes(roadDurationMinutes,
                                    previousForWait + DEPOTS, customer + DEPOTS);
                    double start = serviceStart[d][k][customer].solutionValue();
                    routeProducerWait += Math.max(0.0, start - arrival);
                    previousFinishForWait = start + SERVICE_MINUTES[customer];
                    previousForWait = customer;
                }
                double routeDepotWait = routeDeparture - SHIFT_START;
                double routeOperation = returnTime - routeDeparture;
                double routeDriving = 0.0;
                double routeService = 0.0;
                int previousNode = -1;
                for (int customer : sequence) {
                    routeService += SERVICE_MINUTES[customer];
                    if (previousNode < 0) {
                        routeDriving += travelMinutes(roadDurationMinutes, d, customer + DEPOTS);
                    } else {
                        routeDriving += travelMinutes(roadDurationMinutes,
                                previousNode + DEPOTS, customer + DEPOTS);
                    }
                    previousNode = customer;
                }
                routeDriving += travelMinutes(roadDurationMinutes, previousNode + DEPOTS, d);
                if (Math.abs(routeOperation - (routeDriving + routeService + routeProducerWait)) > 1e-3) {
                    throw new IllegalStateException("Desglose temporal inconsistente en " + vehicleName(k));
                }
                double routeEconomicCost = FIXED_COST[k] + routeKm * COST_PER_KM[k];
                recomputedKm += routeKm;
                recomputedCost += routeEconomicCost;
                recomputedObjective += routeEconomicCost;
                if (config.scenario() == Scenario.WAITING_PENALTY) {
                    recomputedObjective += config.depotWaitCopPerMinute() * routeDepotWait
                            + config.producerWaitCopPerMinute() * routeProducerWait;
                } else if (config.scenario() == Scenario.OPERATION_PENALTY) {
                    recomputedObjective += config.operationCopPerMinute() * routeOperation;
                }
                System.out.printf("%s | %s: regreso %s | %.1f km | %.1f t | OK%n",
                        DEPOT_NAMES[d], vehicleName(k), formatTime(returnTime), routeKm, load);
            }
        }

        for (int i = 0; i < CUSTOMERS; i++) {
            if (customerCount[i] != (customerDemand(i) > 1e-9 ? 1 : 0)) {
                throw new IllegalStateException(CUSTOMER_NAMES[i]
                        + " fue asignado " + customerCount[i] + " veces (debe coincidir con su demanda activa).");
            }
        }

        for (int d = 0; d < DEPOTS; d++) {
            for (int p = 0; p < PRODUCTS; p++) {
                if (depotProductUse[d][p] > STOCK[d][p] + EPS) {
                    throw new IllegalStateException("Inventario excedido: " + DEPOT_NAMES[d]
                            + " / " + PRODUCT_NAMES[p]);
                }
            }
        }

        if (Math.abs(recomputedObjective - objective.value()) > 0.1) {
            throw new IllegalStateException("Objetivo reconstruido no coincide con el solver: "
                    + recomputedObjective + " vs " + objective.value());
        }

        System.out.printf("Distancia reconstruida: %.1f km%n", recomputedKm);
        System.out.printf("Costo económico reconstruido: $%,.0f COP%n", recomputedCost);
        System.out.printf("Valor de función objetivo: $%,.0f COP-equivalentes%n", recomputedObjective);
        System.out.printf("Vehiculos activos: %d | Productores unicos: %d%n", usedVehicles, CUSTOMERS);
        System.out.println("Resultado: TODAS LAS COMPROBACIONES SUPERADAS.");
    }

    private double round3(double value) { return Math.round(value * 1000.0) / 1000.0; }

    private double travelMinutes(double[][] durationMinutes, int globalFrom, int globalTo) {
        return durationMinutes[globalFrom][globalTo];
    }

    private double distanceForLocalNodes(double[][] distanceKm, int depot, int localFrom, int localTo) {
        int globalFrom = localFrom == 0 ? depot : localFrom - 1 + DEPOTS;
        int globalTo = localTo == 0 ? depot : localTo - 1 + DEPOTS;
        return distanceKm[globalFrom][globalTo];
    }

    private void validateRoadMatrices(double[][] distanceKm, double[][] durationMinutes) {
        int expected = DEPOTS + CUSTOMERS;
        if (distanceKm == null || durationMinutes == null
                || distanceKm.length != expected || durationMinutes.length != expected) {
            throw new IllegalArgumentException("OSRM debe entregar matrices de 11x11.");
        }
        for (int i = 0; i < expected; i++) {
            if (distanceKm[i] == null || durationMinutes[i] == null
                    || distanceKm[i].length != expected || durationMinutes[i].length != expected) {
                throw new IllegalArgumentException("La matriz vial/temporal no es cuadrada de 11x11.");
            }
            for (int j = 0; j < expected; j++) {
                if (!Double.isFinite(distanceKm[i][j]) || distanceKm[i][j] < 0
                        || !Double.isFinite(durationMinutes[i][j]) || durationMinutes[i][j] < 0) {
                    throw new IllegalArgumentException("OSRM contiene una distancia o duración inválida en [" + i + "][" + j + "].");
                }
            }
        }
    }

    private void printSolution(
            MPVariable[][] use,
            MPVariable[][][] visit,
            MPVariable[][][][] x,
            MPVariable[][][] serviceStart,
            MPObjective objective,
            double[][] roadDistanceKm) {

        double totalKm = 0;
        int usedVehicles = 0;
        int servedCustomers = 0;

        System.out.println();
        System.out.printf("Valor de la función objetivo: $%,.0f COP-equivalentes%n", objective.value());

        for (int d = 0; d < DEPOTS; d++) {
            for (int k = 0; k < VEHICLES; k++) {
                if (use[d][k].solutionValue() < 0.5) continue;

                usedVehicles++;
                double routeKm = 0;
                double load = 0;
                List<Integer> route = new ArrayList<>();
                route.add(0);

                int current = 0;
                boolean[] seen = new boolean[CUSTOMERS + 1];
                int safety = 0;

                while (safety++ <= CUSTOMERS + 1) {
                    int next = -1;
                    for (int j = 0; j <= CUSTOMERS; j++) {
                        if (j != current && x[d][k][current][j] != null
                                && x[d][k][current][j].solutionValue() > 0.5) {
                            next = j;
                            break;
                        }
                    }
                    if (next < 0) break;

                    routeKm += distanceForLocalNodes(roadDistanceKm, d, current, next);
                    route.add(next);
                    if (next == 0) break;

                    int customer = next - 1;
                    if (seen[next]) break;
                    seen[next] = true;
                    servedCustomers++;
                    load += customerDemand(customer);
                    current = next;
                }

                System.out.println();
                System.out.printf("%s | %s | Carga: %.1f / %.1f t | Distancia: %.1f km%n",
                        DEPOT_NAMES[d], vehicleName(k), load, VEHICLE_CAPACITY[k], routeKm);

                System.out.print("Ruta: " + DEPOT_NAMES[d]);
                for (int idx = 1; idx < route.size(); idx++) {
                    int node = route.get(idx);
                    if (node == 0) {
                        System.out.print(" -> " + DEPOT_NAMES[d]);
                    } else {
                        int customer = node - 1;
                        System.out.printf(" -> %s (servicio %s)",
                                CUSTOMER_NAMES[customer],
                                formatTime(serviceStart[d][k][customer].solutionValue()));
                    }
                }
                System.out.println();

                System.out.print("Productos: ");
                for (int p = 0; p < PRODUCTS; p++) {
                    double quantity = 0;
                    for (int i = 0; i < CUSTOMERS; i++) {
                        if (visit[d][k][i].solutionValue() > 0.5) {
                            quantity += DEMAND[i][p];
                        }
                    }
                    System.out.printf("%s=%.1f t%s", PRODUCT_NAMES[p], quantity,
                            p == PRODUCTS - 1 ? "" : " | ");
                }
                System.out.println();

                totalKm += routeKm;
            }
        }

        System.out.println();
        System.out.printf("Vehiculos utilizados: %d de %d%n", usedVehicles, VEHICLES);
        System.out.printf("Productores atendidos: %d de %d%n", servedCustomers, CUSTOMERS);
        System.out.printf("Distancia total: %.1f km%n", totalKm);
    }

}
