package com.agrodistribuye.catalog;

import java.util.List;

/** Nombres de presentación; los códigos C1..C3 y P1..P8 conservan su identidad. */
public final class NodeNames {
    public static final List<String> DEPOTS = List.of(
            "Distribuidora Norte", "Distribuidora Centro", "Distribuidora Sur");

    private NodeNames() {}

    public static String depotId(String name) {
        int index = DEPOTS.indexOf(name);
        if (index >= 0) return "C" + (index + 1);
        if (List.of("C1", "C2", "C3").contains(name)) return name;
        throw new IllegalArgumentException("Depósito desconocido: " + name);
    }

    public static String name(String id) {
        return switch (id) {
            case "C1", "C2", "C3" -> DEPOTS.get(Integer.parseInt(id.substring(1)) - 1);
            case "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8" -> "Productor " + id;
            default -> throw new IllegalArgumentException("Punto desconocido: " + id);
        };
    }
}
