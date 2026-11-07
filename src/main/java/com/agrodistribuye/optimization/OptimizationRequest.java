package com.agrodistribuye.optimization;

public record OptimizationRequest(PlanningData data, boolean offline, boolean alternatives,
        boolean sensitivity, long timeLimitMs, AlternativeOptions options) {
    public OptimizationRequest {
        if (data == null) data = PlanningData.defaults();
        if (timeLimitMs == 0) timeLimitMs = 30_000;
        if (timeLimitMs < 1000 || timeLimitMs > 60_000)
            throw new IllegalArgumentException("El límite por escenario debe estar entre 1 y 60 segundos.");
        if (options == null) options = AlternativeOptions.defaults();
        if (alternatives && data.vehicleCapacity().length >= 8)
            throw new IllegalArgumentException("Usa hasta 7 vehículos para evaluar la incorporación de uno adicional.");
    }
    public static OptimizationRequest defaults() { return new OptimizationRequest(null, false, false, false, 30_000, null); }
    public record AlternativeOptions(double extraVehicleCapacity, double extraVehicleFixedCost,
            double extraVehicleCostPerKm, double capacityIncreasePercent, double upgradeFixedCostPerVehicle) {
        public AlternativeOptions {
            double[] values = {extraVehicleCapacity, extraVehicleFixedCost, extraVehicleCostPerKm,
                    capacityIncreasePercent, upgradeFixedCostPerVehicle};
            for (double value : values) if (!Double.isFinite(value) || value < 0 || value > 1e9)
                throw new IllegalArgumentException("Costos y capacidades de alternativas: valores finitos no negativos.");
            if (extraVehicleCapacity <= 0 || capacityIncreasePercent <= 0 || capacityIncreasePercent > 100)
                throw new IllegalArgumentException("La capacidad adicional debe ser positiva y su ampliación entre 0 y 100 %.");
        }
        public static AlternativeOptions defaults() { return new AlternativeOptions(5, 70000, 1800, 20, 15000); }
    }
}
