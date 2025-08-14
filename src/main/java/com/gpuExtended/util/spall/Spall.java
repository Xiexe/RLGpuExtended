package com.gpuExtended.util.spall;

import com.gpuExtended.util.ProfileTime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Spall {
    // Implementation of https://github.com/colrdavidson/spall-web/blob/master/spall.h
    public static final ThreadLocal<SpallThreadData> spallThreadData = new ThreadLocal<>();
    public volatile static boolean recordingProfile = false;

    public static void StartProfile() {
        recordingProfile = true;
        System.out.println("Started profile");
    }
    public static void BeginFrame(String name) {
        if (recordingProfile) {
            if (Spall.spallThreadData.get() == null) {
                Spall.spallThreadData.set(new SpallThreadData());
            }
            SpallThreadData data = spallThreadData.get();
            data.PutBeginEvent(name);
        }
    }

    public static void EndFrame() {
        if (recordingProfile) {
            SpallThreadData data = spallThreadData.get();
            if (data != null) {
                data.PutEndEvent(ProfileTime.GetTime());
            }
        }
    }

    public static void SaveProfile() {
        if (!recordingProfile) return;
        recordingProfile = false;
        // TODO: wait for threads!
        SpallThreadData data = spallThreadData.get();
        if (data != null) {
            ByteBuffer buffer = spallThreadData.get().buffer;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-M-d-'UTC'_HH_mm_ss");
            ZonedDateTime utcNow = ZonedDateTime.now(ZoneOffset.UTC);
            String filename = utcNow.format(formatter) + ".spall";
            try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
                 FileChannel channel = raf.getChannel()) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Profile saved to " + filename);
            spallThreadData.remove();
        }
    }
}
