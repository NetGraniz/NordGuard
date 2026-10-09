package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelWorkloadTest {
    @Test void boundedStateAcrossSixHundredSyntheticPlayers() {
        var policy = MovementModelTest.policy();
        var models = new MovementModel[600];
        for (int i = 0; i < models.length; i++) models[i] = new MovementModel();
        long start = System.nanoTime();
        for (int tick = 0; tick < 1200; tick++) for (var model : models) {
            var result = model.accept(MovementModelTest.frame(tick * .3, 64, true, false, false), policy);
            assertTrue(result.flags().isEmpty());
        }
        long elapsed = System.nanoTime() - start;
        System.out.println("NORD_MODEL_WORKLOAD: 720000 pure model samples, elapsed ms=" + elapsed / 1_000_000
                + "; excludes world queries, network, schedulers and real players");
    }
}
