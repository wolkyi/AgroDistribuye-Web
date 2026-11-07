package com.agrodistribuye.optimization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import com.agrodistribuye.engine.AgroDistribuyeMdvRPTW;

@SpringBootTest
class PlanningIntegrationTest {
    @Autowired OptimizationService service;
    @Autowired OptimizationJobs jobs;
    private PlanningData oneCustomer(double quantity) {
        var d = PlanningData.defaults(); double[][] demand = new double[8][4]; demand[0][0] = quantity;
        return new PlanningData(demand,d.stock(),d.depotCapacity(),d.timeWindows(),d.serviceMinutes(),d.vehicleCapacity(),d.fixedCost(),d.costPerKm(),d.provenance(),null);
    }
    private double[][] road() { double[][] m = new double[11][11]; for(int i=0;i<11;i++) for(int j=0;j<11;j++) m[i][j]=i==j?0:1; return m; }
    @Test void invalidEntriesAreRejectedBeforeSolving() {
        var d = PlanningData.defaults(); d.demand()[0][0] = -1;
        assertThrows(IllegalArgumentException.class, () -> new PlanningData(d.demand(),d.stock(),d.depotCapacity(),d.timeWindows(),d.serviceMinutes(),d.vehicleCapacity(),d.fixedCost(),d.costPerKm(),d.provenance(),null));
        assertThrows(IllegalArgumentException.class, () -> new OptimizationRequest(null,true,false,false,999,null));
    }
    @Test void independentInstancesDoNotShareEditedDemand() {
        var first = CompletableFuture.supplyAsync(() -> AgroDistribuyeMdvRPTW.optimize(road(),road(),AgroDistribuyeMdvRPTW.OptimizationConfig.baseline(),oneCustomer(.1),3000));
        var second = CompletableFuture.supplyAsync(() -> AgroDistribuyeMdvRPTW.optimize(road(),road(),AgroDistribuyeMdvRPTW.OptimizationConfig.baseline(),oneCustomer(.2),3000));
        var a=first.join(); var b=second.join();
        assertTrue(a.validated()); assertTrue(b.validated());
        assertEquals(.1,a.routes().getFirst().loadTonnes(),1e-6); assertEquals(.2,b.routes().getFirst().loadTonnes(),1e-6);
        assertEquals(73600,a.totalCostCop(),.01); assertEquals(1,a.servedCustomers());
        assertTrue(a.solverMetrics().variables()>a.solverMetrics().binaryVariables());
        assertEquals(0,a.solverMetrics().relativeGap(),1e-6);
    }
    @Test void zeroDemandHasZeroCostAndNoRoutes() {
        var result=AgroDistribuyeMdvRPTW.optimize(road(),road(),AgroDistribuyeMdvRPTW.OptimizationConfig.baseline(),oneCustomer(0),3000);
        assertTrue(result.validated()); assertEquals(0,result.totalCostCop(),.001); assertTrue(result.routes().isEmpty());
    }
    @Test void offlineStudyRunsAlternativesAndThreeParameterFamilies() {
        var result=service.execute(new OptimizationRequest(oneCustomer(.1),true,true,true,1000,null),(a,b)->{});
        assertEquals(15,result.experiments().size()); assertTrue(result.validated());
        assertEquals("LOCAL_SNAPSHOT",result.run().matrixMode());
        assertEquals(2,result.experiments().stream().filter(e->"ALTERNATIVE".equals(e.category())).count());
        assertEquals(6,result.experiments().stream().filter(e->"SENSITIVITY".equals(e.category())).count());
        assertTrue(result.experiments().stream().allMatch(e->e.validated() && e.servedCustomers()==1));
        assertTrue(result.experiments().stream().filter(e->e.status().equals("OPTIMAL"))
                .allMatch(e->e.solverMetrics().relativeGap()!=null && e.solverMetrics().relativeGap()<=1e-7));
        assertEquals(11,result.run().roadMatrices().distanceKm().length);
    }
    @Test void insufficientInventoryIsInfeasibleNotANetworkError() {
        var d=oneCustomer(9); double[][] stock=new double[3][4];
        var data=new PlanningData(d.demand(),stock,d.depotCapacity(),d.timeWindows(),d.serviceMinutes(),d.vehicleCapacity(),d.fixedCost(),d.costPerKm(),d.provenance(),null);
        var result=service.execute(new OptimizationRequest(data,true,false,false,1000,null),(a,b)->{});
        assertEquals("INFEASIBLE",result.status()); assertEquals(7,result.experiments().size());
        assertTrue(result.experiments().stream().allMatch(e->e.status().equals("INFEASIBLE") && e.message().contains("inventario")));
    }
    @Test void asynchronousProgressRetainsResultsAndRejectsOverlappingJobs() throws Exception {
        var job=jobs.start(new OptimizationRequest(oneCustomer(.1),true,false,false,1000,null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->jobs.start(new OptimizationRequest(oneCustomer(.1),true,false,false,1000,null)));
        long deadline=System.nanoTime()+15_000_000_000L;
        while(jobs.get(job.id()).state().equals("RUNNING") && System.nanoTime()<deadline) Thread.sleep(50);
        var completed=jobs.get(job.id()); assertEquals("COMPLETED",completed.state());
        assertEquals(7,completed.completed()); assertEquals(7,completed.total()); assertTrue(completed.result().validated());
    }
}
