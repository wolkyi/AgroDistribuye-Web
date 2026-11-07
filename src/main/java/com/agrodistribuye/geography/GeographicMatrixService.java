package com.agrodistribuye.geography;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.agrodistribuye.catalog.NodeNames;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeographicMatrixService {
    private static final String OSRM = "https://router.project-osrm.org/table/v1/driving/";
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final RoadSnapshotStore snapshots;

    public GeographicMatrixService(ObjectMapper mapper, RoadSnapshotStore snapshots) {
        this.mapper = mapper;
        this.snapshots = snapshots;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public MatrixResponse buildMatrix() throws Exception {
        List<Point> points = points();
        StringBuilder coords = new StringBuilder();
        for (Point p : points) {
            if (!coords.isEmpty()) coords.append(';');
            coords.append(p.longitude()).append(',').append(p.latitude());
        }
        URI uri = URI.create(OSRM + coords + "?annotations=distance,duration");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90))
                .header("User-Agent", "AgroDistribuye-AcademicPrototype/1.0")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("OSRM respondió HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        if (!"Ok".equals(root.path("code").asString())) throw new IllegalStateException("OSRM no pudo construir la matriz: " + root.path("code").asString());
        JsonNode distances = root.path("distances");
        JsonNode durations = root.path("durations");
        if (!distances.isArray() || !durations.isArray() || distances.size() != points.size() || durations.size() != points.size())
            throw new IllegalStateException("La matriz recibida no tiene dimensión 11x11.");
        List<List<Cell>> matrix = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            List<Cell> row = new ArrayList<>();
            for (int j = 0; j < points.size(); j++) {
                JsonNode d = distances.path(i).path(j), t = durations.path(i).path(j);
                row.add((d.isNumber() && t.isNumber())
                    ? new Cell(round(d.asDouble()/1000.0, 3), round(t.asDouble()/60.0, 2), "OSRM_ROAD_ROUTE")
                    : new Cell(null, null, "NO_ROUTE"));
            }
            matrix.add(List.copyOf(row));
        }
        return new MatrixResponse("OK", "OpenStreetMap road network via OSRM public demo", "driving", OffsetDateTime.now().toString(),
                "Distances are fastest-route distances; durations are estimated travel times. Public demo is for light use.", points, matrix);
    }

    public MatrixResponse buildMatrix(boolean offline) throws Exception {
        if (offline) {
            MatrixResponse saved = snapshots.matrix();
            List<Point> named = saved.points().stream().map(p -> new Point(p.id(), NodeNames.name(p.id()),
                    p.latitude(), p.longitude(), p.dataStatus())).toList();
            return new MatrixResponse(saved.status(), saved.provider(), saved.profile(), saved.generatedAt(),
                    saved.note(), named, saved.matrix());
        }
        MatrixResponse response = buildMatrix();
        toRoadMatrices(response);
        snapshots.saveMatrix(response);
        return response;
    }

    public List<List<Double>> routeCoordinates(String vehicle, String depot, List<String> stops, boolean offline) throws Exception {
        String key = NodeNames.depotId(depot) + "|" + String.join("|", stops);
        List<List<Double>> saved = snapshots.geometry(key);
        if (!saved.isEmpty()) return saved;
        if (offline) return List.of();
        var coordinates = buildRouteGeometry(vehicle, depot, stops).coordinates();
        snapshots.saveGeometry(key, coordinates);
        return coordinates;
    }


    private List<Point> points() {
        return List.of(
            new Point("C1", NodeNames.name("C1"), 2.449371790947889, -76.60225432081373, "USER_PROVIDED_COORDINATE"),
            new Point("C2", NodeNames.name("C2"), 2.448775892614762, -76.60170816842958, "USER_PROVIDED_COORDINATE"),
            new Point("C3", NodeNames.name("C3"), 2.488665777025983, -76.56050874141953, "USER_PROVIDED_COORDINATE"),
            new Point("P1", NodeNames.name("P1"), 2.47733, -76.67206, "REFERENCE_POINT"),
            new Point("P2", NodeNames.name("P2"), 2.53530, -76.56769, "REFERENCE_POINT"),
            new Point("P3", NodeNames.name("P3"), 2.47713, -76.64577, "REFERENCE_POINT"),
            new Point("P4", NodeNames.name("P4"), 2.44744, -76.58350, "REFERENCE_POINT"),
            new Point("P5", NodeNames.name("P5"), 2.43477, -76.62546, "REFERENCE_POINT"),
            new Point("P6", NodeNames.name("P6"), 2.50070, -76.55743, "REFERENCE_POINT"),
            new Point("P7", NodeNames.name("P7"), 2.44574, -76.63679, "REFERENCE_POINT"),
            new Point("P8", NodeNames.name("P8"), 2.52590, -76.55192, "REFERENCE_POINT")
        );
    }

    /** Solicita a OSRM la geometría vial real de una ruta ya optimizada. */
    public RouteGeometry buildRouteGeometry(String vehicle, String depotName, List<String> producerIds) throws Exception {
        List<Point> all = points();
        String depotId = NodeNames.depotId(depotName);
        List<String> ids = new ArrayList<>();
        ids.add(depotId);
        ids.addAll(producerIds);
        ids.add(depotId);
        StringBuilder coordinates = new StringBuilder();
        for (String id : ids) {
            Point point = all.stream().filter(p -> p.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Punto geográfico desconocido: " + id));
            if (!coordinates.isEmpty()) coordinates.append(';');
            coordinates.append(point.longitude()).append(',').append(point.latitude());
        }
        URI uri = URI.create("https://router.project-osrm.org/route/v1/driving/" + coordinates
                + "?overview=full&geometries=geojson&steps=false");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "AgroDistribuye-AcademicPrototype/1.0").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("OSRM geometría HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        if (!"Ok".equals(root.path("code").asString()) || !root.path("routes").isArray()
                || root.path("routes").isEmpty()) {
            throw new IllegalStateException("OSRM no devolvió geometría para " + ids);
        }
        JsonNode coords = root.path("routes").get(0).path("geometry").path("coordinates");
        List<List<Double>> latLon = new ArrayList<>();
        for (JsonNode pair : coords) {
            if (pair.isArray() && pair.size() >= 2) latLon.add(List.of(pair.get(1).asDouble(), pair.get(0).asDouble()));
        }
        if (latLon.size() < 2) throw new IllegalStateException("Geometría vial vacía para " + ids);
        return new RouteGeometry(vehicle, depotName, String.join(" → ", ids), List.copyOf(latLon));
    }

    public record RouteGeometry(String vehicle, String depot, String sequence, List<List<Double>> coordinates) {}

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    /** Convierte la respuesta OSRM a matrices numéricas para OR-Tools, conservando el orden C1..C3,P1..P8. */
    public RoadMatrices toRoadMatrices(MatrixResponse response) {
        int n = response.points().size();
        if (!"OK".equals(response.status()) || n != 11 || response.matrix().size() != n) {
            throw new IllegalStateException("La respuesta OSRM no contiene una matriz válida de 11x11.");
        }
        String[] expectedIds = {"C1", "C2", "C3", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8"};
        double[][] distances = new double[n][n];
        double[][] durations = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (!expectedIds[i].equals(response.points().get(i).id()) || response.matrix().get(i).size() != n) {
                throw new IllegalStateException("El orden o dimensión de la matriz OSRM no coincide con el modelo en la fila " + i + ".");
            }
            for (int j = 0; j < n; j++) {
                Cell cell = response.matrix().get(i).get(j);
                if (cell == null || !"OSRM_ROAD_ROUTE".equals(cell.status())
                        || cell.distanceKm() == null || cell.durationMinutes() == null) {
                    throw new IllegalStateException("No existe ruta vial válida entre " + expectedIds[i] + " y " + expectedIds[j] + ".");
                }
                distances[i][j] = cell.distanceKm();
                durations[i][j] = cell.durationMinutes();
            }
        }
        return new RoadMatrices(distances, durations);
    }

    public record RoadMatrices(double[][] distanceKm, double[][] durationMinutes) {}

    public record Point(String id, String name, double latitude, double longitude, String dataStatus) {}
    public record Cell(Double distanceKm, Double durationMinutes, String status) {}
    public record MatrixResponse(String status, String provider, String profile, String generatedAt,
                                 String note, List<Point> points, List<List<Cell>> matrix) {}
}
