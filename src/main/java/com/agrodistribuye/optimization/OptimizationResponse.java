package com.agrodistribuye.optimization;

import java.time.OffsetDateTime;
import java.util.List;

import com.agrodistribuye.engine.AgroDistribuyeMdvRPTW.RouteResult;
import com.agrodistribuye.geography.GeographicMatrixService.RouteGeometry;

public record OptimizationResponse(
        String status,
        boolean validated,
        String dataMode,
        String engine,
        OffsetDateTime executedAt,
        double totalCostCop,
        double totalDistanceKm,
        int usedVehicles,
        int servedCustomers,
        long solvingTimeMs,
        List<RouteResult> routes,
        List<RouteGeometry> routeGeometries,
        String message,
        List<ExperimentResult> experiments,
        RunContext run
) {
    public record ExperimentResult(
            String scenario,
            String label,
            double depotWaitCopPerMinute,
            double producerWaitCopPerMinute,
            double operationCopPerMinute,
            String status,
            boolean validated,
            double totalCostCop,
            double objectiveValue,
            double totalDistanceKm,
            double totalDepotWaitingMinutes,
            double totalProducerWaitingMinutes,
            double totalDrivingMinutes,
            double totalServiceMinutes,
            double totalOperationMinutes,
            int usedVehicles,
            int servedCustomers,
            long solvingTimeMs,
            List<ExperimentRoute> routes,
            String message,
            String category,
            String parameter,
            Double parameterValue,
            com.agrodistribuye.engine.AgroDistribuyeMdvRPTW.SolverMetrics solverMetrics
    ) {}
    public record RunContext(OptimizationRequest request, String matrixProvider, String matrixGeneratedAt,
            String matrixMode, int activeCustomers, String javaVersion, int availableProcessors,
            com.agrodistribuye.geography.GeographicMatrixService.RoadMatrices roadMatrices, String modelVersion) {}
    public record ExperimentRoute(
            String depot, String vehicle, double capacityTonnes, double loadTonnes,
            double distanceKm, double routeCostCop, String departureTime, String returnTime,
            double depotWaitingMinutes, double waitingMinutes, double drivingMinutes,
            double serviceMinutes, double operationMinutes,
            java.util.Map<String, Double> productLoadsTonnes,
            List<com.agrodistribuye.engine.AgroDistribuyeMdvRPTW.StopResult> stops,
            List<List<Double>> geometry
    ) {}
}
