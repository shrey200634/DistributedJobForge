package com.distributedjobforge.scheduler_service.service;

import com.distributedjobforge.scheduler_service.dto.BatchJobItem;
import com.distributedjobforge.scheduler_service.exception.CycleDepededncyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
@Slf4j
@Service
public class DagResolverService {
    public List<BatchJobItem> topologicalSort(List<BatchJobItem> items) {
        Map<String, BatchJobItem> byId = new LinkedHashMap<>();
        for (BatchJobItem item : items) {
            if (byId.put(item.clientRefId(), item) != null) {
                throw new CycleDepededncyException(
                        "Duplicate clientRefId in batch: '" + item.clientRefId() + "'");
            }
        }
        //  Validate all dependsOn references exist in this batch
        for (BatchJobItem item : items) {
            if (item.dependsOn() == null) continue;
            for (String dep : item.dependsOn()) {
                if (!byId.containsKey(dep)) {
                    throw new CycleDepededncyException(
                            "Job '" + item.clientRefId() +
                                    "' references unknown dependency '" + dep + "'");
                }
            }
        }
        //  Kahn's algorithm
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (String id : byId.keySet()) {
            inDegree.put(id, 0);
            children.put(id, new ArrayList<>());
        }
        for (BatchJobItem item : items) {
            if (item.dependsOn() == null) continue;
            for (String parent : item.dependsOn()) {
                inDegree.merge(item.clientRefId(), 1, Integer::sum);
                children.get(parent).add(item.clientRefId());
            }
        }
        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<BatchJobItem> sorted = new ArrayList<>(items.size());
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(byId.get(current));
            for (String child : children.get(current)) {
                if (inDegree.merge(child, -1, Integer::sum) == 0) {
                    queue.add(child);
                }
            }
        }
        //Cycle check
        if (sorted.size() != items.size()) {
            throw new CycleDepededncyException(
                    "Cycle detected in job dependency graph. " +
                            "Could order " + sorted.size() + " of " + items.size() + " jobs.");
        }
        log.info("DAG resolved: {} jobs in topological order (roots={})",
                sorted.size(),
                sorted.stream()
                        .filter(j -> j.dependsOn() == null || j.dependsOn().isEmpty())
                        .count());
        return sorted;
    }
}