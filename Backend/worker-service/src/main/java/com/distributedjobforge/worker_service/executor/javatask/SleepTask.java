package com.distributedjobforge.worker_service.executor.javatask;

import java.util.Map;

public class SleepTask implements JavaTask{

    @Override
    public String name (){
        return "sleep ";
    }

    @Override
    public String run(Map<String, Object> args) throws InterruptedException {
        int seconds = ((Number) args.getOrDefault("seconds", 1)).intValue();
        Thread.sleep(seconds * 1000L);
        return "slept for " + seconds + "s";
    }
}
