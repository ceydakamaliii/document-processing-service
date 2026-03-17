package com.example.docprocessing.pipeline.simulated;

import java.util.Random;

public class Utils {
    private static final Random RANDOM = new Random();
    private Utils() {}
    public static void sleepRandom(long minMs, long maxMs) {
        long delay = minMs + RANDOM.nextInt((int) (maxMs - minMs));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    public static boolean shouldFail(int failurePercentage) {
        return RANDOM.nextInt(100) < failurePercentage;
    }

    
}
