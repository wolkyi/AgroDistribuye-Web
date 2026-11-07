package com.agrodistribuye.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    @Test
    void catalogContainsExpectedSimulatedEntities() {
        CatalogResponse response = new CatalogService().getCatalog();
        assertEquals("SIMULATED", response.dataMode());
        assertEquals(3, response.depots().size());
        assertEquals(8, response.customers().size());
        assertEquals(4, response.products().size());
        assertEquals(6, response.vehicles().size());
        for (var depot : response.depots()) assertEquals(NodeNames.name(depot.id()), depot.name());
        for (var customer : response.customers()) assertEquals(NodeNames.name(customer.id()), customer.name());
    }
}
