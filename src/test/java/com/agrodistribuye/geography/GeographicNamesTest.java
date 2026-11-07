package com.agrodistribuye.geography;

import com.agrodistribuye.catalog.NodeNames;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class GeographicNamesTest {
    @Test
    void renamedDepotsKeepTheirOwnOfflineRoadGeometries() throws Exception {
        var mapper = new ObjectMapper();
        var snapshots = new RoadSnapshotStore(mapper);
        var service = new GeographicMatrixService(mapper, snapshots);
        var stops = List.of(List.of("P3", "P1"), List.of("P3", "P7"), List.of("P8", "P6"));
        for (int i = 0; i < 3; i++) {
            String id = "C" + (i + 1), name = NodeNames.name(id);
            var expected = snapshots.geometry(id + "|" + String.join("|", stops.get(i)));
            assertFalse(expected.isEmpty(), "Falta geometría de " + id);
            assertEquals(expected, service.routeCoordinates("V1", name, stops.get(i), true));
            assertEquals(id, NodeNames.depotId(name));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.routeCoordinates("V1", "Depósito desconocido", List.of("P1"), true));
    }

    @Test
    void offlineMatrixUsesGenericNamesAndPreservesPointOrder() throws Exception {
        var mapper = new ObjectMapper();
        var service = new GeographicMatrixService(mapper, new RoadSnapshotStore(mapper));
        var matrix = service.buildMatrix(true);
        assertEquals(11, matrix.points().size());
        for (var point : matrix.points()) assertEquals(NodeNames.name(point.id()), point.name());
        assertEquals(11, service.toRoadMatrices(matrix).distanceKm().length);
    }
}
