/*
 * Copyright (c) 2022 Abex
 * Copyright 2010 JogAmp Community.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gpuExtended.util;

public class Mat4
{
	private Mat4()
	{
	}

	public static float[] identity()
	{
		return new float[]
			{
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0,
				0, 0, 0, 1,
			};
	}

	public static float[] scale(float sx, float sy, float sz)
	{
		return new float[]
			{
				sx, 0, 0, 0,
				0, sy, 0, 0,
				0, 0, sz, 0,
				0, 0, 0, 1,
			};
	}

	public static float[] translate(float tx, float ty, float tz)
	{
		return new float[]
			{
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0,
				tx, ty, tz, 1,
			};
	}

	public static float[] rotateX(float rx)
	{
		float s = (float) Math.sin(rx);
		float c = (float) Math.cos(rx);

		return new float[]
			{
				1, 0, 0, 0,
				0, c, s, 0,
				0, -s, c, 0,
				0, 0, 0, 1,
			};
	}

	public static float[] rotateY(float ry)
	{
		float s = (float) Math.sin(ry);
		float c = (float) Math.cos(ry);

		return new float[]
			{
				c, 0, -s, 0,
				0, 1, 0, 0,
				s, 0, c, 0,
				0, 0, 0, 1,
			};
	}

	public static float[] projection(float w, float h, float n)
	{
		return new float[]
			{
				2 / w, 0, 0, 0,
				0, 2 / h, 0, 0,
				0, 0, -1, -1,
				0, 0, -2 * n, 0
			};
	}

	public static float[] ortho(float w, float h, float n)
	{
		return new float[]
				{
						2 / w, 0, 0, 0,
						0, 2 / h, 0, 0,
						0, 0, -2 / n, 0,
						0, 0, 0, 1
				};
	}

	public static void mul(final float[] a, final float[] b)
	{
		final float b00 = b[0 + 0 * 4];
		final float b10 = b[1 + 0 * 4];
		final float b20 = b[2 + 0 * 4];
		final float b30 = b[3 + 0 * 4];
		final float b01 = b[0 + 1 * 4];
		final float b11 = b[1 + 1 * 4];
		final float b21 = b[2 + 1 * 4];
		final float b31 = b[3 + 1 * 4];
		final float b02 = b[0 + 2 * 4];
		final float b12 = b[1 + 2 * 4];
		final float b22 = b[2 + 2 * 4];
		final float b32 = b[3 + 2 * 4];
		final float b03 = b[0 + 3 * 4];
		final float b13 = b[1 + 3 * 4];
		final float b23 = b[2 + 3 * 4];
		final float b33 = b[3 + 3 * 4];

		float ai0 = a[0 * 4]; // row-0 of a
		float ai1 = a[1 * 4];
		float ai2 = a[2 * 4];
		float ai3 = a[3 * 4];
		a[0 * 4] = ai0 * b00 + ai1 * b10 + ai2 * b20 + ai3 * b30;
		a[1 * 4] = ai0 * b01 + ai1 * b11 + ai2 * b21 + ai3 * b31;
		a[2 * 4] = ai0 * b02 + ai1 * b12 + ai2 * b22 + ai3 * b32;
		a[3 * 4] = ai0 * b03 + ai1 * b13 + ai2 * b23 + ai3 * b33;

		ai0 = a[1 + 0 * 4]; // row-1 of a
		ai1 = a[1 + 1 * 4];
		ai2 = a[1 + 2 * 4];
		ai3 = a[1 + 3 * 4];
		a[1 + 0 * 4] = ai0 * b00 + ai1 * b10 + ai2 * b20 + ai3 * b30;
		a[1 + 1 * 4] = ai0 * b01 + ai1 * b11 + ai2 * b21 + ai3 * b31;
		a[1 + 2 * 4] = ai0 * b02 + ai1 * b12 + ai2 * b22 + ai3 * b32;
		a[1 + 3 * 4] = ai0 * b03 + ai1 * b13 + ai2 * b23 + ai3 * b33;

		ai0 = a[2 + 0 * 4]; // row-2 of a
		ai1 = a[2 + 1 * 4];
		ai2 = a[2 + 2 * 4];
		ai3 = a[2 + 3 * 4];
		a[2 + 0 * 4] = ai0 * b00 + ai1 * b10 + ai2 * b20 + ai3 * b30;
		a[2 + 1 * 4] = ai0 * b01 + ai1 * b11 + ai2 * b21 + ai3 * b31;
		a[2 + 2 * 4] = ai0 * b02 + ai1 * b12 + ai2 * b22 + ai3 * b32;
		a[2 + 3 * 4] = ai0 * b03 + ai1 * b13 + ai2 * b23 + ai3 * b33;

		ai0 = a[3 + 0 * 4]; // row-3 of a
		ai1 = a[3 + 1 * 4];
		ai2 = a[3 + 2 * 4];
		ai3 = a[3 + 3 * 4];
		a[3 + 0 * 4] = ai0 * b00 + ai1 * b10 + ai2 * b20 + ai3 * b30;
		a[3 + 1 * 4] = ai0 * b01 + ai1 * b11 + ai2 * b21 + ai3 * b31;
		a[3 + 2 * 4] = ai0 * b02 + ai1 * b12 + ai2 * b22 + ai3 * b32;
		a[3 + 3 * 4] = ai0 * b03 + ai1 * b13 + ai2 * b23 + ai3 * b33;
	}

	public static float[] transform(float[] matrix, float[] position)
	{
		float x = matrix[0] * position[0] + matrix[1] * position[1] + matrix[2] * position[2] + matrix[3];
		float y = matrix[4] * position[0] + matrix[5] * position[1] + matrix[6] * position[2] + matrix[7];
		float z = matrix[8] * position[0] + matrix[9] * position[1] + matrix[10] * position[2] + matrix[11];
		return new float[]{x, y, z};
	}

	public static float[] inverse(float[] m)
	{
		float[] inv = new float[16];
		float det;

		inv[0] = m[5]  * m[10] * m[15] -
				m[5]  * m[11] * m[14] -
				m[9]  * m[6]  * m[15] +
				m[9]  * m[7]  * m[14] +
				m[13] * m[6]  * m[11] -
				m[13] * m[7]  * m[10];

		inv[4] = -m[4]  * m[10] * m[15] +
				m[4]  * m[11] * m[14] +
				m[8]  * m[6]  * m[15] -
				m[8]  * m[7]  * m[14] -
				m[12] * m[6]  * m[11] +
				m[12] * m[7]  * m[10];

		inv[8] = m[4]  * m[9] * m[15] -
				m[4]  * m[11] * m[13] -
				m[8]  * m[5] * m[15] +
				m[8]  * m[7] * m[13] +
				m[12] * m[5] * m[11] -
				m[12] * m[7] * m[9];

		inv[12] = -m[4]  * m[9] * m[14] +
				m[4]  * m[10] * m[13] +
				m[8]  * m[5] * m[14] -
				m[8]  * m[6] * m[13] -
				m[12] * m[5] * m[10] +
				m[12] * m[6] * m[9];

		inv[1] = -m[1]  * m[10] * m[15] +
				m[1]  * m[11] * m[14] +
				m[9]  * m[2] * m[15] -
				m[9]  * m[3] * m[14] -
				m[13] * m[2] * m[11] +
				m[13] * m[3] * m[10];

		inv[5] = m[0]  * m[10] * m[15] -
				m[0]  * m[11] * m[14] -
				m[8]  * m[2] * m[15] +
				m[8]  * m[3] * m[14] +
				m[12] * m[2] * m[11] -
				m[12] * m[3] * m[10];

		inv[9] = -m[0]  * m[9] * m[15] +
				m[0]  * m[11] * m[13] +
				m[8]  * m[1] * m[15] -
				m[8]  * m[3] * m[13] -
				m[12] * m[1] * m[11] +
				m[12] * m[3] * m[9];

		inv[13] = m[0]  * m[9] * m[14] -
				m[0]  * m[10] * m[13] -
				m[8]  * m[1] * m[14] +
				m[8]  * m[2] * m[13] +
				m[12] * m[1] * m[10] -
				m[12] * m[2] * m[9];

		inv[2] = m[1]  * m[6] * m[15] -
				m[1]  * m[7] * m[14] -
				m[5]  * m[2] * m[15] +
				m[5]  * m[3] * m[14] +
				m[13] * m[2] * m[7] -
				m[13] * m[3] * m[6];

		inv[6] = -m[0]  * m[6] * m[15] +
				m[0]  * m[7] * m[14] +
				m[4]  * m[2] * m[15] -
				m[4]  * m[3] * m[14] -
				m[12] * m[2] * m[7] +
				m[12] * m[3] * m[6];

		inv[10] = m[0]  * m[5] * m[15] -
				m[0]  * m[7] * m[13] -
				m[4]  * m[1] * m[15] +
				m[4]  * m[3] * m[13] +
				m[12] * m[1] * m[7] -
				m[12] * m[3] * m[5];

		inv[14] = -m[0]  * m[5] * m[14] +
				m[0]  * m[6] * m[13] +
				m[4]  * m[1] * m[14] -
				m[4]  * m[2] * m[13] -
				m[12] * m[1] * m[6] +
				m[12] * m[2] * m[5];

		inv[3] = -m[1] * m[6] * m[11] +
				m[1] * m[7] * m[10] +
				m[5] * m[2] * m[11] -
				m[5] * m[3] * m[10] -
				m[9] * m[2] * m[7] +
				m[9] * m[3] * m[6];

		inv[7] = m[0] * m[6] * m[11] -
				m[0] * m[7] * m[10] -
				m[4] * m[2] * m[11] +
				m[4] * m[3] * m[10] +
				m[8] * m[2] * m[7] -
				m[8] * m[3] * m[6];

		inv[11] = -m[0] * m[5] * m[11] +
				m[0] * m[7] * m[9] +
				m[4] * m[1] * m[11] -
				m[4] * m[3] * m[9] -
				m[8] * m[1] * m[7] +
				m[8] * m[3] * m[5];

		inv[15] = m[0] * m[5] * m[10] -
				m[0] * m[6] * m[9] -
				m[4] * m[1] * m[10] +
				m[4] * m[2] * m[9] +
				m[8] * m[1] * m[6] -
				m[8] * m[2] * m[5];

		det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];

		if (det == 0)
			return null;

		det = 1.0f / det;

		for (int i = 0; i < 16; i++)
			inv[i] = inv[i] * det;

		return inv;
	}
}
