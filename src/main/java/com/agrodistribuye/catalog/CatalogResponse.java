package com.agrodistribuye.catalog;

import java.util.List;

public record CatalogResponse(
        String dataMode,
        List<Depot> depots,
        List<Customer> customers,
        List<Product> products,
        List<Vehicle> vehicles
) {
    public record Depot(String id, String name, double storageCapacityTonnes) {}
    public record Customer(String id, String name, String timeWindow, int serviceMinutes) {}
    public record Product(String id, String name) {}
    public record Vehicle(String id, String type, double capacityTonnes,
                          long fixedCostCop, long costPerKmCop) {}
}
