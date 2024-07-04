
package com.gpuExtended.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class GpuIntBuffer
{
	private IntBuffer buffer = allocateDirect(65536);

	public void put(float x, float y, float z, int w)
	{
		buffer.put(Float.floatToIntBits(x))
				.put(Float.floatToIntBits(y))
				.put(Float.floatToIntBits(z))
				.put(w);
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

			IntBuffer newB = allocateDirect(capacity);
			buffer.flip();
			newB.put(buffer);
			buffer = newB;
		}
	}

	public IntBuffer getBuffer()
	{
		return buffer;
	}

	public static IntBuffer allocateDirect(int size)
	{
		return ByteBuffer.allocateDirect(size * Integer.BYTES)
			.order(ByteOrder.nativeOrder())
			.asIntBuffer();
	}
}
