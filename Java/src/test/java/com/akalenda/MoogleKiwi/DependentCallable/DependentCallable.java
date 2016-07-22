package com.akalenda.MoogleKiwi.DependentCallable;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * A wrapper for a task that is dependent on the completion of other tasks.
 */
public class DependentCallable<T> implements Callable<T> {

    private ImmutableSet.Builder<DependentCallable> dependencies = new ImmutableSet.Builder<>();
    private ImmutableSet.Builder<DependentCallable> dependants = new ImmutableSet.Builder<>();
    private Callable<T> lambda;

    public DependentCallable(Callable<T> lambda) {
        this.lambda = lambda;
    }

    public DependentCallable addDependencies(List<DependentCallable> deps) {
        dependencies.addAll(deps);
        for (DependentCallable dep : deps)
            dep.addDependant(this);
        return this;
    }

    private DependentCallable addDependency(DependentCallable dep) {
        dependencies.add(dep);
        dep.addDependant(this);
        return this;
    }

    private DependentCallable addDependants(List<DependentCallable> deps) {
        dependants.addAll(deps);
        for (DependentCallable dep : deps)
            dep.addDependency(this);
        return this;
    }

    private DependentCallable addDependant(DependentCallable dep) {
        dependants.add(dep);
        dep.addDependency(this);
        return this;
    }

    private T callNow() throws Exception {
        T result = lambda.call();
        for (DependentCallable dep : dependants.build())
            ;
        return result;
    }

    @Override
    public T call() throws Exception {
        ImmutableSet<DependentCallable> deps = dependencies.build();
        if (deps.size() == 0)
            return callNow();
        return null; //TODO
    }
}
