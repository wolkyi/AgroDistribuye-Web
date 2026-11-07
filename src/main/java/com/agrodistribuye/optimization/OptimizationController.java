package com.agrodistribuye.optimization;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/optimizaciones")
public class OptimizationController {
    private final OptimizationService optimizationService;
    private final OptimizationJobs jobs;

    public OptimizationController(OptimizationService optimizationService, OptimizationJobs jobs) {
        this.optimizationService = optimizationService;
        this.jobs = jobs;
    }

    @PostMapping("/ejecutar")
    public OptimizationResponse execute(@RequestBody(required = false) OptimizationRequest request) {
        return optimizationService.execute(request == null ? OptimizationRequest.defaults() : request, (a,b) -> {});
    }
    @GetMapping("/datos") public PlanningData defaults() { return PlanningData.defaults(); }
    @PostMapping("/datos/validar") public PlanningData validate(@RequestBody PlanningData data) { return data; }
    @PostMapping("/trabajos") public OptimizationJobs.Progress start(@RequestBody OptimizationRequest request) { return jobs.start(request); }
    @GetMapping("/trabajos/{id}") public OptimizationJobs.Progress progress(@PathVariable("id") String id) { return jobs.get(id); }
}
