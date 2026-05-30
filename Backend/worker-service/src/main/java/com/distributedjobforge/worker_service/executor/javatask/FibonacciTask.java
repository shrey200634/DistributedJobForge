package com.distributedjobforge.worker_service.executor.javatask;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FibonacciTask implements JavaTask {

    @Override
    public String name() {
        return "fibonacci";
    }

    @Override
    public String run(Map<String, Object> args) {
        // args come from JSON, so numbers arrive as Number; coerce safely.
        int n = ((Number) args.getOrDefault("n", 10)).intValue();

        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0, got " + n);
        }

        long a = 0, b = 1;
        for (int i = 0; i < n; i++) {
            long next = a + b;
            a = b;
            b = next;
        }
        return "fib(" + n + ") = " + a;
    }
}
