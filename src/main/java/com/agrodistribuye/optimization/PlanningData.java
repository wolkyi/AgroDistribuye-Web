package com.agrodistribuye.optimization;

import java.util.Arrays;

/** Datos de una jornada; nodos geográficos C1..C3 y P1..P8 estables. */
public record PlanningData(double[][] demand, double[][] stock, double[] depotCapacity,
        int[][] timeWindows, int[] serviceMinutes, double[] vehicleCapacity,
        double[] fixedCost, double[] costPerKm, String provenance, Double actualPlanCostCop) {
    public PlanningData {
        matrix(demand, 8, 4, "Demanda (t)"); matrix(stock, 3, 4, "Inventario (t)");
        vector(depotCapacity, 3, "Capacidad de depósitos (t)", true);
        if (vehicleCapacity == null || vehicleCapacity.length < 1 || vehicleCapacity.length > 8)
            throw new IllegalArgumentException("La flota debe contener entre 1 y 8 vehículos.");
        vector(vehicleCapacity, vehicleCapacity.length, "Capacidad vehicular (t)", true);
        vector(fixedCost, vehicleCapacity.length, "Costo fijo (COP)", false);
        vector(costPerKm, vehicleCapacity.length, "Costo por km (COP/km)", false);
        if (timeWindows == null || timeWindows.length != 8 || serviceMinutes == null || serviceMinutes.length != 8)
            throw new IllegalArgumentException("Se requieren horarios y servicios para P1–P8.");
        for (int i = 0; i < 8; i++) {
            if (timeWindows[i] == null || timeWindows[i].length != 2 || timeWindows[i][0] < 420
                    || timeWindows[i][1] > 1080 || timeWindows[i][0] >= timeWindows[i][1]
                    || serviceMinutes[i] < 1 || serviceMinutes[i] > timeWindows[i][1] - timeWindows[i][0])
                throw new IllegalArgumentException("P" + (i + 1) + ": ventana entre 07:00 y 18:00, con tiempo suficiente para el servicio.");
        }
        for (int d = 0; d < 3; d++) if (Arrays.stream(stock[d]).sum() > depotCapacity[d] + 1e-5)
            throw new IllegalArgumentException("C" + (d + 1) + ": el inventario excede la capacidad de almacenamiento.");
        if (provenance == null || provenance.isBlank() || provenance.length() > 500)
            throw new IllegalArgumentException("Indica el origen y naturaleza de los datos (máximo 500 caracteres).");
        if (actualPlanCostCop != null && (!Double.isFinite(actualPlanCostCop) || actualPlanCostCop <= 0 || actualPlanCostCop > 1e12))
            throw new IllegalArgumentException("El costo del plan actual debe ser positivo en COP, o quedar vacío.");
        demand = copy(demand); stock = copy(stock); depotCapacity = depotCapacity.clone();
        timeWindows = Arrays.stream(timeWindows).map(int[]::clone).toArray(int[][]::new);
        serviceMinutes = serviceMinutes.clone(); vehicleCapacity = vehicleCapacity.clone();
        fixedCost = fixedCost.clone(); costPerKm = costPerKm.clone(); provenance = provenance.trim();
    }
    private static void vector(double[] values, int n, String name, boolean positive) {
        if (values == null || values.length != n) throw new IllegalArgumentException(name + ": dimensión incorrecta.");
        for (double x : values) if (!Double.isFinite(x) || x < 0 || (positive && x == 0) || x > 1e9)
            throw new IllegalArgumentException(name + ": usa valores finitos " + (positive ? "positivos" : "no negativos") + " hasta 1.000 millones.");
    }
    private static void matrix(double[][] values, int n, int m, String name) {
        if (values == null || values.length != n) throw new IllegalArgumentException(name + ": dimensión incorrecta.");
        for (double[] row : values) vector(row, m, name, false);
    }
    private static double[][] copy(double[][] a) { return Arrays.stream(a).map(double[]::clone).toArray(double[][]::new); }
    public PlanningData scaled(double demandFactor, double capacityFactor, double kmFactor) {
        double[][] newDemand = copy(demand);
        for (double[] row : newDemand) for (int p = 0; p < row.length; p++) row[p] *= demandFactor;
        return new PlanningData(newDemand, stock, depotCapacity, timeWindows, serviceMinutes,
                Arrays.stream(vehicleCapacity).map(x -> x * capacityFactor).toArray(), fixedCost,
                Arrays.stream(costPerKm).map(x -> x * kmFactor).toArray(), provenance, actualPlanCostCop);
    }
    public PlanningData withExtraVehicle(double capacity, double fixed, double perKm) {
        if (vehicleCapacity.length == 8) throw new IllegalArgumentException("La alternativa de vehículo adicional admite como máximo 7 vehículos de entrada.");
        double[] caps = Arrays.copyOf(vehicleCapacity, vehicleCapacity.length + 1);
        double[] costs = Arrays.copyOf(fixedCost, fixedCost.length + 1);
        double[] kms = Arrays.copyOf(costPerKm, costPerKm.length + 1);
        caps[caps.length - 1] = capacity; costs[costs.length - 1] = fixed; kms[kms.length - 1] = perKm;
        return new PlanningData(demand, stock, depotCapacity, timeWindows, serviceMinutes, caps, costs, kms, provenance, actualPlanCostCop);
    }
    public PlanningData upgraded(double percent, double extraFixed) {
        return new PlanningData(demand, stock, depotCapacity, timeWindows, serviceMinutes,
                Arrays.stream(vehicleCapacity).map(x -> x * (1 + percent / 100)).toArray(),
                Arrays.stream(fixedCost).map(x -> x + extraFixed).toArray(), costPerKm, provenance, actualPlanCostCop);
    }
    public static PlanningData defaults() {
        return new PlanningData(new double[][]{{2.2,.8,1.2,.4},{2,.9,1,.5},{2,.8,1.1,.4},{1.6,.7,1,.4},
                {1.8,1.1,1.4,.6},{1.8,1,1.3,.5},{1.5,1.4,1.5,.6},{1.6,1.3,1.5,.6}},
                new double[][]{{7,3.5,4,1.5},{6,3.5,5,2},{4,3,3,1.5}}, new double[]{20,25,18},
                new int[][]{{480,600},{540,720},{480,660},{600,780},{780,960},{840,1020},{480,720},{780,1020}},
                new int[]{30,30,30,30,30,30,40,40}, new double[]{5,5,5,10,10,10},
                new double[]{70000,70000,70000,110000,110000,110000},
                new double[]{1800,1800,1800,2500,2500,2500}, "Datos económicos y operativos simulados para análisis académico.", null);
    }
}
