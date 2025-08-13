package com.gpuExtended.util.spall;

public class ProfileFrame implements AutoCloseable {
    public ProfileFrame(String name) {
        if (Spall.recordingProfile) {
            if (Spall.spallThreadData.get() == null) {
                Spall.spallThreadData.set(new SpallThreadData());
            }
            Spall.spallThreadData.get().PutBeginEvent(name);
        }
    }

    @Override
    public void close() {
        if (Spall.recordingProfile) {
            long endTime = System.nanoTime();
            if (Spall.spallThreadData.get() != null) {
                Spall.spallThreadData.get().PutEndEvent(endTime);
            }
        }
    }
}
