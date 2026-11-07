package com.agrodistribuye.optimization;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.agrodistribuye.engine.AgroDistribuyeMdvRPTW;
import com.agrodistribuye.engine.AgroDistribuyeMdvRPTW.*;
import com.agrodistribuye.geography.GeographicMatrixService;
import com.agrodistribuye.geography.GeographicMatrixService.*;
import com.agrodistribuye.optimization.OptimizationResponse.*;

@Service
public class OptimizationService {
    private final GeographicMatrixService geography;
    private final Semaphore executionLock = new Semaphore(1);
    public OptimizationService(GeographicMatrixService geography) { this.geography = geography; }
    public OptimizationResponse execute() { return execute(OptimizationRequest.defaults(), (a,b) -> {}); }
    public OptimizationResponse execute(OptimizationRequest request, BiConsumer<Integer,Integer> progress) {
        if (!executionLock.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Ya hay una ejecución en curso. Espera a que termine.");
        long start = System.nanoTime();
        try {
            List<ExperimentSpec> specs = specs(request);
            progress.accept(0, specs.size());
            MatrixResponse matrix = geography.buildMatrix(request.offline());
            RoadMatrices road = geography.toRoadMatrices(matrix);
            RunContext context = new RunContext(request, matrix.provider(), matrix.generatedAt(),
                    request.offline() ? "LOCAL_SNAPSHOT" : "OSRM_ONLINE", activeCustomers(request.data()),
                    System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(), road, "0.8.4");
            int threads = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
            try (ExecutorService solverPool = Executors.newFixedThreadPool(threads);
                 ExecutorService geometryPool = Executors.newFixedThreadPool(2)) {
                AtomicInteger completed = new AtomicInteger();
                ConcurrentMap<String, CompletableFuture<List<List<Double>>>> cache = new ConcurrentHashMap<>();
                List<CompletableFuture<ScenarioExecution>> futures = specs.stream().map(spec -> CompletableFuture.supplyAsync(() -> {
                    ScenarioExecution solved = solve(spec, road, request, cache, geometryPool);
                    progress.accept(completed.incrementAndGet(), specs.size());
                    return solved;
                }, solverPool)).toList();
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                List<ScenarioExecution> results = futures.stream().map(CompletableFuture::join).toList();
                List<ExperimentResult> experiments = results.stream().map(ScenarioExecution::experiment).toList();
                OptimizationResult baseline = results.getFirst().optimization();
                List<RouteGeometry> geometries = experiments.getFirst().routes().stream().filter(r -> r.geometry().size() >= 2)
                        .map(r -> new RouteGeometry(r.vehicle(), r.depot(), r.depot() + " → " + r.stops().stream().map(StopResult::producerId).toList(), r.geometry())).toList();
                String message = baseline == null || !baseline.validated() ? experiments.getFirst().message() : null;
                return new OptimizationResponse(experiments.getFirst().status(), baseline != null && baseline.validated(),
                        "USER_INPUT_WITH_OSRM_ROAD_MATRIX", "OR-Tools 9.12.4544 / SCIP", OffsetDateTime.now(),
                        baseline == null ? 0 : baseline.totalCostCop(), baseline == null ? 0 : baseline.totalDistanceKm(),
                        baseline == null ? 0 : baseline.usedVehicles(), baseline == null ? 0 : baseline.servedCustomers(),
                        (System.nanoTime() - start) / 1_000_000, baseline == null ? List.of() : baseline.routes(), geometries,
                        message, experiments, context);
            }
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) {
            return new OptimizationResponse("ERROR", false, "USER_INPUT_WITH_OSRM_ROAD_MATRIX", "OR-Tools / SCIP", OffsetDateTime.now(),
                    0,0,0,0,(System.nanoTime()-start)/1_000_000,List.of(),List.of(),
                    "No se pudo completar la ejecución: " + rootMessage(ex) + ". Si falló OSRM, selecciona el modo sin conexión.",List.of(),null);
        } finally { executionLock.release(); }
    }
    private List<ExperimentSpec> specs(OptimizationRequest request) {
        PlanningData data = request.data();
        List<ExperimentSpec> all = new ArrayList<>();
        all.add(new ExperimentSpec("A_BASELINE", "A. Referencia del modelo · salida 07:00", OptimizationConfig.baseline(),data,"REFERENCE",null,null));
        for (double value : new double[]{50,100,300}) all.add(new ExperimentSpec("B_DEPOT_"+(int)value,
                "Espera depósito " +(int)value+" / productor 200 COP/min",new OptimizationConfig(Scenario.WAITING_PENALTY,value,200,0),data,"TEMPORAL","depotWaitCopPerMinute",value));
        for (double value : new double[]{50,100,200}) all.add(new ExperimentSpec("C_OPERATION_"+(int)value,
                "Duración de operación · " +(int)value+" COP/min",new OptimizationConfig(Scenario.OPERATION_PENALTY,0,0,value),data,"TEMPORAL","operationCopPerMinute",value));
        if (request.alternatives()) {
            var o = request.options();
            all.add(new ExperimentSpec("ALT_EXTRA_VEHICLE", "Incorporar un vehículo de " + o.extraVehicleCapacity() + " t; costo fijo y km incluidos",
                    OptimizationConfig.baseline(),data.withExtraVehicle(o.extraVehicleCapacity(),o.extraVehicleFixedCost(),o.extraVehicleCostPerKm()),"ALTERNATIVE","extraVehicleCapacity",o.extraVehicleCapacity()));
            all.add(new ExperimentSpec("ALT_CAPACITY", "Ampliar capacidad " + o.capacityIncreasePercent() + " %; sobrecosto por vehículo usado " + o.upgradeFixedCostPerVehicle() + " COP",
                    OptimizationConfig.baseline(),data.upgraded(o.capacityIncreasePercent(),o.upgradeFixedCostPerVehicle()),"ALTERNATIVE","capacityIncreasePercent",o.capacityIncreasePercent()));
        }
        if (request.sensitivity()) for (String parameter : List.of("demand","capacity","costPerKm")) for (double factor : new double[]{.8,1.2}) {
            PlanningData variant = data.scaled(parameter.equals("demand") ? factor : 1,parameter.equals("capacity") ? factor : 1,parameter.equals("costPerKm") ? factor : 1);
            String name = switch(parameter) { case "demand" -> "Demanda"; case "capacity" -> "Capacidad vehicular"; default -> "Costo por km"; };
            all.add(new ExperimentSpec("SENS_"+parameter+"_"+(int)(factor*100), name + " · " +(int)(factor*100)+" %",OptimizationConfig.baseline(),variant,"SENSITIVITY",parameter,factor));
        }
        return List.copyOf(all);
    }
    private ScenarioExecution solve(ExperimentSpec spec, RoadMatrices road, OptimizationRequest request,
            ConcurrentMap<String,CompletableFuture<List<List<Double>>>> cache, ExecutorService geometryPool) {
        String diagnostic = infeasibility(spec.data());
        if (diagnostic != null) return new ScenarioExecution(null, failed(spec,"INFEASIBLE",diagnostic));
        try {
            OptimizationResult result = AgroDistribuyeMdvRPTW.optimize(road.distanceKm(), road.durationMinutes(),spec.config(),spec.data(),request.timeLimitMs());
            List<ExperimentRoute> routes = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (RouteResult route : result.routes()) {
                List<String> stops = route.stops().stream().map(StopResult::producerId).toList();
                String key = route.depot()+"|"+String.join("|",stops);
                CompletableFuture<List<List<Double>>> future = cache.computeIfAbsent(key,k -> CompletableFuture.supplyAsync(() -> {
                    try { return geography.routeCoordinates(route.vehicle(),route.depot(),stops,request.offline()); }
                    catch (Exception ex) { throw new CompletionException(ex); }
                },geometryPool));
                List<List<Double>> geometry;
                try { geometry = future.join(); } catch (Exception ex) { geometry = List.of(); warnings.add(route.vehicle()+": "+rootMessage(ex)); }
                if (geometry.isEmpty()) warnings.add(route.vehicle()+": sin geometría vial guardada; el esquema se identifica con línea discontinua.");
                routes.add(new ExperimentRoute(route.depot(),route.vehicle(),route.capacityTonnes(),route.loadTonnes(),route.distanceKm(),route.routeCostCop(),
                        route.departureTime(),route.returnTime(),route.depotWaitingMinutes(),route.waitingMinutes(),route.drivingMinutes(),route.serviceMinutes(),
                        route.operationMinutes(),route.productLoadsTonnes(),route.stops(),geometry));
            }
            if (!result.validated()) warnings.add(result.status().equals("INFEASIBLE")
                    ? "No existe una solución con estas capacidades, inventarios y ventanas. Revisa esas restricciones."
                    : "El límite terminó sin solución validada. Aumenta el tiempo; esto no demuestra infactibilidad.");
            return new ScenarioExecution(result,new ExperimentResult(spec.id(),spec.label(),spec.config().depotWaitCopPerMinute(),spec.config().producerWaitCopPerMinute(),
                    spec.config().operationCopPerMinute(),result.status(),result.validated(),result.totalCostCop(),result.objectiveValue(),result.totalDistanceKm(),
                    result.totalDepotWaitingMinutes(),result.totalProducerWaitingMinutes(),result.totalDrivingMinutes(),result.totalServiceMinutes(),result.totalOperationMinutes(),
                    result.usedVehicles(),result.servedCustomers(),result.solvingTimeMs(),List.copyOf(routes),warnings.isEmpty()?null:String.join(" | ",warnings),
                    spec.category(),spec.parameter(),spec.parameterValue(),result.solverMetrics()));
        } catch (Exception ex) { return new ScenarioExecution(null,failed(spec,"ERROR",rootMessage(ex))); }
    }
    private ExperimentResult failed(ExperimentSpec spec, String status, String message) {
        var c = spec.config();
        return new ExperimentResult(spec.id(),spec.label(),c.depotWaitCopPerMinute(),c.producerWaitCopPerMinute(),c.operationCopPerMinute(),
                status,false,0,0,0,0,0,0,0,0,0,0,0,List.of(),message,spec.category(),spec.parameter(),spec.parameterValue(),null);
    }
    static int activeCustomers(PlanningData data) { return (int)Arrays.stream(data.demand()).filter(row -> Arrays.stream(row).sum() > 1e-9).count(); }
    static String infeasibility(PlanningData data) {
        double max = Arrays.stream(data.vehicleCapacity()).max().orElse(0);
        double total = 0;
        for (int i = 0; i < 8; i++) {
            double demand = Arrays.stream(data.demand()[i]).sum(); total += demand;
            if (demand > max + 1e-5) return "P"+(i+1)+": pedido de "+demand+" t superior al vehículo más grande ("+max+" t). No se permiten entregas divididas.";
        }
        if (total > Arrays.stream(data.vehicleCapacity()).sum() + 1e-5) return "La demanda total supera la capacidad de la flota para una jornada con un viaje por vehículo.";
        for (int p = 0; p < 4; p++) {
            double demand = 0, stock = 0;
            for (double[] row : data.demand()) demand += row[p];
            for (double[] row : data.stock()) stock += row[p];
            if (demand > stock + 1e-5) return "Producto PR"+(p+1)+": demanda "+demand+" t superior al inventario total "+stock+" t.";
        }
        return null;
    }
    private static String rootMessage(Throwable ex) { while(ex.getCause()!=null && ex.getCause()!=ex) ex=ex.getCause(); return ex.getMessage()==null?ex.toString():ex.getMessage(); }
    private record ExperimentSpec(String id,String label,OptimizationConfig config,PlanningData data,String category,String parameter,Double parameterValue) {}
    private record ScenarioExecution(OptimizationResult optimization,ExperimentResult experiment) {}
}
