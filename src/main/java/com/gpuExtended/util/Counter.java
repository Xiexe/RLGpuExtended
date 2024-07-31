package com.gpuExtended.util;

public class Counter {
    private int count = 0;
    private int lastCount = 0;

    public void increment() {
        this.lastCount = this.count;
        this.count++;
    }

    public void decrement() {
        this.lastCount = this.count;
        this.count--;
    }

    public int getCount() {
        return this.lastCount;
    }

    public void reset() {
        this.lastCount = this.count;
        this.count = 0;
    }
}
