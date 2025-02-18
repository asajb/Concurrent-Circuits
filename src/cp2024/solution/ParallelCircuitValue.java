package cp2024.solution;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import cp2024.circuit.CircuitValue;

/*
 * ParallelCircuitValue implements the CircuitValue interface and provides a parallel implementation of the getValue method.
 * It uses a Future object to store the result of the computation and provides a getValue method that returns the result.
 */
public class ParallelCircuitValue implements CircuitValue {
    private final boolean broken;
    private final Future<Boolean> future;

    public ParallelCircuitValue(Future<Boolean> future) {
        this.broken = false;
        this.future = future;
    }

    public ParallelCircuitValue(boolean broken) {
        this.broken = broken;
        this.future = null;
    }

    @Override
    public boolean getValue() throws InterruptedException {
        if (broken) {
            throw new InterruptedException();
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw e;  // Re-throw the InterruptedException
        } catch (ExecutionException e) {
            throw new InterruptedException();
        } catch (CancellationException e) {
            throw new InterruptedException(); // Handle interrupted tasks
        }
    }
}