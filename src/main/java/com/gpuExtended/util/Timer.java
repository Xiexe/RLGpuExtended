package com.gpuExtended.util;

public class Timer {
    private long startTime;
    private double elapsedTime;
    private double lastElapsedTime;
    private boolean running;
    Counter counter = new Counter();

    public Timer() {
        this.running = false;
    }

    // Start the timer
    public void start() {
        if(running)
            return;

        this.startTime = System.nanoTime();
        this.running = true;
    }

    public void stop() {
        if(!running)
            return;

        this.elapsedTime += (System.nanoTime() - startTime);  // 100 is the time it takes to stop the timer, roughly.
        this.running = false;
        counter.increment();
    }

    public void reset() {
        this.running = false;
        this.startTime = 0;

        this.lastElapsedTime = this.elapsedTime;
        this.elapsedTime = 0;
        counter.reset();
    }

    public double getElapsedNanoSeconds() {
        return Math.max(0, this.lastElapsedTime);
    }

    public double getElapsedMilliseconds() {
        return getElapsedNanoSeconds() / 1000000f;
    }

    public double getElapsedTimeSeconds() {
        return getElapsedNanoSeconds() / 1000000000f;
    }
    public int getCount() {
        return counter.getCount();
    }
}