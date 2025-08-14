package com.gpuExtended.util.spall;

import com.gpuExtended.util.ProfileTime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public class SpallThreadData {
    public ByteBuffer buffer;

    final int BEGIN_EVENT_SIZE = 20;
    final byte BEGIN_EVENT_TYPE = 3;
    final int END_EVENT_SIZE = 17;

    final byte END_EVENT_TYPE = 4;

    public SpallThreadData() {
        buffer = ByteBuffer.allocateDirect(65536).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0x0BADF00D); // magic header
        buffer.putLong(1); // version=1
        buffer.putDouble(ProfileTime.GetTimestampUnit()); // timestamp unit
        buffer.putLong(0);
    }

    public void PutBeginEvent(String name) {
        ByteBuffer nameBuffer = StandardCharsets.US_ASCII.encode(name);
        if (nameBuffer.limit() > 256) {
            nameBuffer.limit(256);
        }

        ensureCapacity(BEGIN_EVENT_SIZE + nameBuffer.limit());
        buffer.put(BEGIN_EVENT_TYPE);
        buffer.put((byte)0); // category
        buffer.putInt(0); // pid
        buffer.putInt(0); // tid
        buffer.putDouble(ProfileTime.GetTime());
        buffer.put((byte)nameBuffer.limit()); // name length
        buffer.put((byte)0); // args length
        buffer.put(nameBuffer);
    }

    public void PutEndEvent(long timestampNs) {
        ensureCapacity(END_EVENT_SIZE);
        buffer.put(END_EVENT_TYPE);
        buffer.putInt(0); // pid
        buffer.putInt(0); // tid
        buffer.putDouble(timestampNs);
    }

    public void ensureCapacity(int size)
    {
        int capacity = buffer.capacity();
        final int position = buffer.position();
        if ((capacity - position) < size)
        {
            do
            {
                capacity *= 2;
            }
            while ((capacity - position) < size);

            ByteBuffer newB = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
            buffer.flip();
            newB.put(buffer);
            buffer = newB;
        }
    }
}
