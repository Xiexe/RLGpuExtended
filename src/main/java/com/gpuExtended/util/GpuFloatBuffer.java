
package com.gpuExtended.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GpuFloatBuffer
{
	private FloatBuffer buffer = allocateDirect(65536);

	public void put(float s, float t, float p, float q)
	{
		buffer.put(s).put(t).put(p).put(q);
	}

	public void flip()
	{
		buffer.flip();
	}

	public void clear()
	{
		buffer.clear();
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

			FloatBuffer newB = allocateDirect(capacity);
			buffer.flip();
			newB.put(buffer);
			buffer = newB;
		}
	}

	public FloatBuffer getBuffer()
	{
		return buffer;
	}

	public static FloatBuffer allocateDirect(int size)
	{
		return ByteBuffer.allocateDirect(size * Float.BYTES)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer();
	}
}
