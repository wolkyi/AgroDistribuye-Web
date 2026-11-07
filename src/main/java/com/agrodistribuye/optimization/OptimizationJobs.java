package com.agrodistribuye.optimization;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PreDestroy;

@Service
public class OptimizationJobs {
    private final OptimizationService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    public OptimizationJobs(OptimizationService service) { this.service = service; }
    public synchronized Progress start(OptimizationRequest request) {
        if (jobs.values().stream().anyMatch(j -> j.state.equals("RUNNING")))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Hay un cálculo en curso. Espera a que termine.");
        while (jobs.size() >= 3) jobs.remove(jobs.keySet().iterator().next());
        Job job = new Job(UUID.randomUUID().toString()); jobs.put(job.id, job);
        worker.submit(() -> {
            try {
                job.result = service.execute(request, (done,total) -> { synchronized(job) { job.completed = Math.max(job.completed,done); job.total = total; } });
                job.state = "COMPLETED";
            } catch (Exception ex) { job.error = ex.getMessage(); job.state = "FAILED"; }
        });
        return job.snapshot();
    }
    public synchronized Progress get(String id) {
        Job job = jobs.get(id);
        if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejecución no encontrada. Inicia una nueva.");
        return job.snapshot();
    }
    @PreDestroy public void close() { worker.shutdownNow(); }
    public record Progress(String id, String state, int completed, int total, OffsetDateTime startedAt, OptimizationResponse result, String error) {}
    private static class Job {
        final String id; final OffsetDateTime startedAt = OffsetDateTime.now();
        volatile String state = "RUNNING"; volatile int completed, total;
        volatile OptimizationResponse result; volatile String error;
        Job(String id) { this.id = id; }
        synchronized Progress snapshot() { return new Progress(id,state,completed,total,startedAt,result,error); }
    }
}
