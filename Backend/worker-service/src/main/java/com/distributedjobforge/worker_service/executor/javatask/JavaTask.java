package com.distributedjobforge.worker_service.executor.javatask;

import java.util.Map;

/**
 * Contract that every runnable Java task must implement.
 *
 * A JAVA_CLASS job does NOT ship code over the wire. Instead, the worker
 * bundles a fixed set of trusted tasks (each implementing this interface),
 * and the job payload picks one by name. This makes execution safe — only
 * pre-registered tasks can ever run.
 */
public interface JavaTask {

    /**
     * A unique, stable name used to look this task up from a payload.
     * e.g. "fibonacci", "sleep". The payload's "taskName" must match this.
     */
    String name();

    /**
     * Run the task.
     *
     * @param args arbitrary input arguments pulled from the job payload's "args" map
     * @return a human-readable result string (becomes the job's stdout)
     * @throws Exception if the task fails — the executor turns this into a FAILED result
     */
    String run(Map<String, Object> args) throws Exception;
}
