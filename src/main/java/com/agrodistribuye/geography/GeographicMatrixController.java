package com.agrodistribuye.geography;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/geografia")
public class GeographicMatrixController {
    private final GeographicMatrixService service;
    public GeographicMatrixController(GeographicMatrixService service) { this.service = service; }

    @GetMapping("/matriz-vial")
    public GeographicMatrixService.MatrixResponse matrix() {
        try { return service.buildMatrix(); }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No fue posible obtener la matriz vial de OSRM: " + e.getMessage(), e);
        }
    }
}
