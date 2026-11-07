package com.agrodistribuye.catalog;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    public CatalogResponse getCatalog() {
        return new CatalogResponse(
                "SIMULATED",
                List.of(
                        new CatalogResponse.Depot("C1", NodeNames.name("C1"), 20),
                        new CatalogResponse.Depot("C2", NodeNames.name("C2"), 25),
                        new CatalogResponse.Depot("C3", NodeNames.name("C3"), 18)
                ),
                List.of(
                        new CatalogResponse.Customer("P1", NodeNames.name("P1"), "08:00-10:00", 30),
                        new CatalogResponse.Customer("P2", NodeNames.name("P2"), "09:00-12:00", 30),
                        new CatalogResponse.Customer("P3", NodeNames.name("P3"), "08:00-11:00", 30),
                        new CatalogResponse.Customer("P4", NodeNames.name("P4"), "10:00-13:00", 30),
                        new CatalogResponse.Customer("P5", NodeNames.name("P5"), "13:00-16:00", 30),
                        new CatalogResponse.Customer("P6", NodeNames.name("P6"), "14:00-17:00", 30),
                        new CatalogResponse.Customer("P7", NodeNames.name("P7"), "08:00-12:00", 40),
                        new CatalogResponse.Customer("P8", NodeNames.name("P8"), "13:00-17:00", 40)
                ),
                List.of(
                        new CatalogResponse.Product("PR1", "Fertilizantes"),
                        new CatalogResponse.Product("PR2", "Cal agrícola"),
                        new CatalogResponse.Product("PR3", "Abonos orgánicos"),
                        new CatalogResponse.Product("PR4", "Semillas")
                ),
                List.of(
                        new CatalogResponse.Vehicle("V1", "Pequeño", 5, 70000, 1800),
                        new CatalogResponse.Vehicle("V2", "Pequeño", 5, 70000, 1800),
                        new CatalogResponse.Vehicle("V3", "Pequeño", 5, 70000, 1800),
                        new CatalogResponse.Vehicle("V4", "Grande", 10, 110000, 2500),
                        new CatalogResponse.Vehicle("V5", "Grande", 10, 110000, 2500),
                        new CatalogResponse.Vehicle("V6", "Grande", 10, 110000, 2500)
                )
        );
    }
}
