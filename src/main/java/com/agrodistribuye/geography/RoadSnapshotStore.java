package com.agrodistribuye.geography;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.agrodistribuye.geography.GeographicMatrixService.MatrixResponse;

/** Caché de red vial. Nunca sustituye las distancias por líneas rectas. */
@Component
public class RoadSnapshotStore {
    private final ObjectMapper mapper;
    private final Path directory = Path.of("data", "matrices");
    private final Map<String, List<List<Double>>> geometries = new LinkedHashMap<>();
    public RoadSnapshotStore(ObjectMapper mapper) {
        this.mapper = mapper;
        loadGeometryResource();
        Path saved = directory.resolve("geometries-cache.json");
        if (Files.exists(saved)) try { loadGeometries(Files.readString(saved)); } catch (Exception ignored) { }
    }
    private void loadGeometryResource() {
        try (var input = new ClassPathResource("offline/geometries.json").getInputStream()) {
            loadGeometries(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
    private void loadGeometries(String json) throws Exception {
        var root = mapper.readTree(json.replace("\uFEFF", ""));
        for (String key : root.propertyNames()) {
            var coordinates = root.path(key);
            java.util.ArrayList<List<Double>> points = new java.util.ArrayList<>();
            for (var pair : coordinates) if (pair.isArray() && pair.size() == 2)
                points.add(List.of(pair.get(0).asDouble(), pair.get(1).asDouble()));
            if (points.size() >= 2) geometries.put(key, List.copyOf(points));
        }
    }
    public synchronized MatrixResponse matrix() throws Exception {
        Path saved = directory.resolve("matrix-cache.json");
        if (Files.exists(saved)) try { return mapper.readValue(Files.readString(saved), MatrixResponse.class); } catch (Exception ignored) { }
        try (var input = new ClassPathResource("offline/matrix.json").getInputStream()) {
            return mapper.readValue(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF", ""), MatrixResponse.class);
        }
    }
    public synchronized void saveMatrix(MatrixResponse response) {
        save("matrix-cache.json", response);
    }
    public synchronized List<List<Double>> geometry(String key) { return geometries.getOrDefault(key, List.of()); }
    public synchronized void saveGeometry(String key, List<List<Double>> coordinates) {
        geometries.put(key, coordinates);
        save("geometries-cache.json", geometries);
    }
    private void save(String filename, Object value) {
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve(filename + ".tmp");
            Files.writeString(temp, mapper.writeValueAsString(value));
            Files.move(temp, directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            System.err.println("No se pudo persistir la caché vial: " + ex.getMessage());
        }
    }
}
