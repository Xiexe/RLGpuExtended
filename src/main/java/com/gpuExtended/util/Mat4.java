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

import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.camera.Frustum;
import com.gpuExtended.rendering.camera.Plane;

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

	/**
	 * Creates a view matrix that looks from an eye position towards a target.
	 * This is used to position and orient the camera in the world.
	 *
	 * The result is a matrix that transforms world-space coordinates into view-space.
	 *
	 * @param eye    The position of the camera. A float[3] array {x, y, z}.
	 * @param center The point in the world the camera is looking at. A float[3] array {x, y, z}.
	 * @param up     The "up" direction of the world (usually {0, 1, 0}). A float[3] array.
	 * @return A column-major 4x4 view matrix as a float[16] array.
	 */
	public static float[] lookAt(Vector3 eye, Vector3 center, Vector3 up) {
		// 1. Create the forward vector (z-axis of camera space)
		// In a right-handed system, the camera looks down its negative z-axis.
		// So, we calculate the vector pointing from the center to the eye.
		Vector3 zAxis = Vector3.Subtract(eye, center).Normalize();

		// 2. Create the right vector (x-axis of camera space)
		// This is the cross product of the world's up vector and our new z-axis.
		Vector3 xAxis = Vector3.Cross(up, zAxis).Normalize();

		// 3. Create the camera's true up vector (y-axis of camera space)
		// This is the cross product of our new z-axis and x-axis to ensure orthogonality.
		Vector3 yAxis = Vector3.Cross(zAxis, xAxis);

		// The view matrix is composed of the three basis vectors (xAxis, yAxis, zAxis)
		// and a translation component. The translation part moves the world so that
		// the 'eye' is at the origin. This is done by taking the dot product of the
		// axes with the eye position.

		return new float[]{
				// Column 1 (X-axis)
				xAxis.x,
				yAxis.x,
				zAxis.x,
				0,

				// Column 2 (Y-axis)
				xAxis.y,
				yAxis.y,
				zAxis.y,
				0,

				// Column 3 (Z-axis)
				xAxis.z,
				yAxis.z,
				zAxis.z,
				0,

				// Column 4 (Translation)
				-xAxis.Dot(eye),
				-yAxis.Dot(eye),
				-zAxis.Dot(eye),
				1
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

	public static void mulVec(float[] out, float[] mat4, float[] vec4) {
		float a =
				mat4[0 * 4 + 0] * vec4[0] +
						mat4[1 * 4 + 0] * vec4[1] +
						mat4[2 * 4 + 0] * vec4[2] +
						mat4[3 * 4 + 0] * vec4[3];
		float b =
				mat4[0 * 4 + 1] * vec4[0] +
						mat4[1 * 4 + 1] * vec4[1] +
						mat4[2 * 4 + 1] * vec4[2] +
						mat4[3 * 4 + 1] * vec4[3];
		float c =
				mat4[0 * 4 + 2] * vec4[0] +
						mat4[1 * 4 + 2] * vec4[1] +
						mat4[2 * 4 + 2] * vec4[2] +
						mat4[3 * 4 + 2] * vec4[3];
		float d =
				mat4[0 * 4 + 3] * vec4[0] +
						mat4[1 * 4 + 3] * vec4[1] +
						mat4[2 * 4 + 3] * vec4[2] +
						mat4[3 * 4 + 3] * vec4[3];
		out[0] = a;
		out[1] = b;
		out[2] = c;
		out[3] = d;
	}

	public static void projectVec(float[] out, float[] mat4, float[] vec4) {
		mulVec(out, mat4, vec4);
		if (out[3] != 0) {
			// The 4th component should retain information about whether the
			// point lies behind the camera
			float reciprocal = 1 / Math.abs(out[3]);
			for (int i = 0; i < 4; i++)
				out[i] *= reciprocal;
		}
	}

	public static float[] perspective(float w, float h, float n) {
		return new float[] {
				2 / w, 0, 0, 0,
				0, -2 / h, 0, 0,
				0, 0, 0, 1,
				0, 0, 2 * n, 0
		};
	}

	public static float[] ortho(float width, float height, float near, float far)
	{
		return new float[]
		{
				2 / width, 0, 0, 0,
				0, 2 / height, 0, 0,
				0, 0, -1f / (far), 0,
				0, 0, 0, 1
		};
	}

	/**
	 * Creates a perspective projection matrix.
	 * This matrix simulates depth by making objects farther away appear smaller.
	 *
	 * @param fovy   The vertical field of view angle, in radians.
	 * @param aspect The aspect ratio of the viewport (width / height).
	 * @param near   The distance to the near clipping plane. Must be positive.
	 * @param far    The distance to the far clipping plane. Must be positive.
	 * @return A column-major 4x4 perspective matrix as a float[16] array.
	 */
	public static float[] perspective(float fovy, float aspect, float near, float far) {
		float[] m = new float[16];

		float tanHalfFovy = (float) Math.tan(fovy / 2.0f);

		m[0] = 1.0f / (aspect * tanHalfFovy);
		m[1] = 0.0f;
		m[2] = 0.0f;
		m[3] = 0.0f;

		m[4] = 0.0f;
		m[5] = 1.0f / (tanHalfFovy);
		m[6] = 0.0f;
		m[7] = 0.0f;

		m[8] = 0.0f;
		m[9] = 0.0f;
		m[10] = -(far + near) / (far - near);
		m[11] = -1.0f;

		m[12] = 0.0f;
		m[13] = 0.0f;
		m[14] = -(2.0f * far * near) / (far - near);
		m[15] = 0.0f;

		return m;
	}

	/**
	 * Creates an orthographic projection matrix.
	 * This matrix defines a 3D box-shaped viewing volume. Objects are rendered
	 * without perspective distortion, which is useful for 2D elements or technical drawings.
	 *
	 * @param left   The coordinate of the left vertical clipping plane.
	 * @param right  The coordinate of the right vertical clipping plane.
	 * @param bottom The coordinate of the bottom horizontal clipping plane.
	 * @param top    The coordinate of the top horizontal clipping plane.
	 * @param near   The distance to the near depth clipping plane.
	 * @param far    The distance to the far depth clipping plane.
	 * @return A column-major 4x4 orthographic matrix as a float[16] array.
	 */
	public static float[] orthographic(float left, float right, float bottom, float top, float near, float far) {
		float[] m = new float[16];

		float r_l = 1.0f / (right - left);
		float t_b = 1.0f / (top - bottom);
		float f_n = 1.0f / (far - near);

		m[0] = 2.0f * r_l;
		m[1] = 0.0f;
		m[2] = 0.0f;
		m[3] = 0.0f;

		m[4] = 0.0f;
		m[5] = 2.0f * t_b;
		m[6] = 0.0f;
		m[7] = 0.0f;

		m[8] = 0.0f;
		m[9] = 0.0f;
		m[10] = -2.0f * f_n; // The negative is because we're mapping to a right-handed NDC
		m[11] = 0.0f;

		m[12] = -(right + left) * r_l;
		m[13] = -(top + bottom) * t_b;
		m[14] = -(far + near) * f_n;
		m[15] = 1.0f;

		return m;
	}

	/**
	 * Multiplies two 4x4 matrices and returns the result in a new float[16] array.
	 * The multiplication order is a * b.
	 *
	 * @param a The left-hand side matrix.
	 * @param b The right-hand side matrix.
	 * @return A new float[16] array containing the result.
	 */
	public static float[] multiply(final float[] a, final float[] b) {
		float[] result = new float[16];

		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				float sum = 0;
				for (int i = 0; i < 4; i++) {
					// For column-major: result[col*4 + row] = sum(a[i*4 + row] * b[col*4 + i])
					sum += a[i*4 + row] * b[col*4 + i];
				}
				result[col*4 + row] = sum;
			}
		}
		return result;
	}


	/**
	 * Extracts the 6 planes of the viewing frustum from a combined view-projection matrix.
	 * The planes are normalized, which is useful for distance calculations.
	 *
	 * @param vpMatrix The combined view * projection matrix.
	 * @return A Frustum object containing the six planes.
	 */
	public static Frustum extractFrustumPlanes(float[] vpMatrix) {
		float[] m = vpMatrix;

		// The plane equations are derived from the rows of the transposed VP matrix.
		// Each row (m00, m01, m02, m03) corresponds to Ax + By + Cz + Dw = 0 in clip space.

		// Left Plane: Row 4 + Row 1
		Plane left = Plane.fromCoefficients(
				m[3] + m[0],
				m[7] + m[4],
				m[11] + m[8],
				m[15] + m[12]
		);

		// Right Plane: Row 4 - Row 1
		Plane right = Plane.fromCoefficients(
				m[3] - m[0],
				m[7] - m[4],
				m[11] - m[8],
				m[15] - m[12]
		);

		// Bottom Plane: Row 4 + Row 2
		Plane bottom = Plane.fromCoefficients(
				m[3] + m[1],
				m[7] + m[5],
				m[11] + m[9],
				m[15] + m[13]
		);

		// Top Plane: Row 4 - Row 2
		Plane top = Plane.fromCoefficients(
				m[3] - m[1],
				m[7] - m[5],
				m[11] - m[9],
				m[15] - m[13]
		);

		// Near Plane: Row 4 + Row 3
		Plane near = Plane.fromCoefficients(
				m[3] + m[2],
				m[7] + m[6],
				m[11] + m[10],
				m[15] + m[14]
		);

		// Far Plane: Row 4 - Row 3
		Plane far = Plane.fromCoefficients(
				m[3] - m[2],
				m[7] - m[6],
				m[11] - m[10],
				m[15] - m[14]
		);

		return new Frustum(left, right, bottom, top, near, far);
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
