package cp2024.solution;

import cp2024.circuit.CircuitSolver;
import cp2024.circuit.CircuitValue;
import cp2024.circuit.NodeType;
import cp2024.circuit.ThresholdNode;
import cp2024.circuit.LeafNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cp2024.circuit.Circuit;
import cp2024.circuit.CircuitNode;

/*
 * ParallelCircuitSolver implements the CircuitSolver interface and provides a parallel implementation of the solve method.
 * It uses an ExecutorService to submit tasks to a thread pool and uses a CompletionService to process the results of these tasks.
 * The solve method recursively solves the circuit by submitting tasks to the ExecutorService and processing the results using the CompletionService.
 */
public class ParallelCircuitSolver implements CircuitSolver {
    private final ExecutorService executorService = Executors.newCachedThreadPool(); // Shared executor
    private volatile boolean acceptComputations = true;

    @Override
    public CircuitValue solve(Circuit c) {
        if (!acceptComputations){
            return new ParallelCircuitValue(true);
        }

        Future<Boolean> future = executorService.submit(() -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            return recursiveSolve(c.getRoot());
        });

        return new ParallelCircuitValue(future);
    }

    // The stop method is used to stop the executor service and prevent further computations.
    @Override
    public void stop() {
        acceptComputations = false;
        executorService.shutdownNow();
    }

    // The recursiveSolve method is used to recursively solve the circuit nodes.
    private boolean recursiveSolve(CircuitNode n) throws InterruptedException {
        if (n.getType() == NodeType.LEAF)
            return ((LeafNode) n).getValue();

        CircuitNode[] args = n.getArgs();

        return switch (n.getType()) {
            case IF -> solveIF(args);
            case AND -> solveAND(args);
            case OR -> solveOR(args);
            case GT -> solveGT(args, ((ThresholdNode) n).getThreshold());
            case LT -> solveLT(args, ((ThresholdNode) n).getThreshold());
            case NOT -> solveNOT(args);
            default -> throw new RuntimeException("Illegal type " + n.getType());
        };
    }

    // The solveIF, solveAND, solveOR, solveGT, solveLT, and solveNOT methods are used to solve the circuit nodes based on their type.
    private boolean solveNOT(CircuitNode[] args) throws InterruptedException {
        return !recursiveSolve(args[0]);
    }
    private boolean solveLT(CircuitNode[] args, int threshold) throws InterruptedException {
        return solveWithThreshold(args, threshold, false); // For LT, we use gotTrue < threshold
    }
    private boolean solveGT(CircuitNode[] args, int threshold) throws InterruptedException {
        return solveWithThreshold(args, threshold, true); // For GT, we use gotTrue > threshold
    }
    private boolean solveOR(CircuitNode[] args) throws InterruptedException {
        return solveWithThreshold(args, 0, true); // For OR, we use gotTrue > 0
    }
    private boolean solveAND(CircuitNode[] args) throws InterruptedException {
        return solveWithThreshold(args, args.length - 1, true); // For AND, we use gotTrue >= args.length <=> gotTrue > args.length - 1
    }
    private boolean solveIF(CircuitNode[] args) throws InterruptedException {
        return solveWithThreshold(new CircuitNode[]{args[0]}, 0, true)? 
            solveWithThreshold(new CircuitNode[]{args[1]}, 0, true) : 
            solveWithThreshold(new CircuitNode[]{args[2]}, 0, true);
    }

    // The solveWithThreshold method is used to solve the circuit nodes based on a threshold value.
    private boolean solveWithThreshold(CircuitNode[] args, int threshold, boolean isGreaterThan) throws InterruptedException {
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);
        List<Future<Boolean>> futures = new ArrayList<>();
        int gotTrue = 0;
        int remaining = args.length;

        // Submit tasks
        for (CircuitNode arg : args) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            Future<Boolean> future = completionService.submit(() -> recursiveSolve(arg));
            futures.add(future);
        }
    
        // Process results as they come in
        try {
            for (int i = 0; i < args.length; ++i) {
                Future<Boolean> future = completionService.take();
                if (future.get()) gotTrue++;
                remaining--;

                // Check if we have met the threshold
                if (isGreaterThan) {
                    if (gotTrue > threshold || (remaining + gotTrue <= threshold)) {
                        futures.forEach(f -> f.cancel(true)); // Cancel remaining tasks
                        break;
                    }
                } else {
                    if (gotTrue >= threshold || (remaining + gotTrue < threshold)) {
                        futures.forEach(f -> f.cancel(true)); // Cancel remaining tasks
                        break;
                    }
                }
            }
        } catch (CancellationException e) {
            throw new InterruptedException(); // Handle interrupted tasks
        } catch (InterruptedException e){
            throw new InterruptedException(); // Handle interrupted tasks
        } catch (ExecutionException e) {
            throw new InterruptedException();
        } finally {
            futures.forEach(f -> f.cancel(true)); // Ensure all tasks are canceled
        }
        return isGreaterThan ? gotTrue > threshold : gotTrue < threshold;
    }
    
}
