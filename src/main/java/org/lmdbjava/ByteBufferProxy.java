/*
 * Copyright 2016 The LmdbJava Project, http://lmdbjava.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lmdbjava;

import static java.lang.ThreadLocal.withInitial;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import static java.nio.ByteBuffer.allocateDirect;
import java.util.ArrayDeque;
import static java.util.Objects.requireNonNull;
import jnr.ffi.Pointer;
import static org.lmdbjava.Env.SHOULD_CHECK;
import static org.lmdbjava.UnsafeAccess.UNSAFE;

/**
 * {@link ByteBuffer}-based proxy.
 * <p>
 * There are two concrete {@link ByteBuffer} proxy implementations available:
 * <ul>
 * <li>A "fast" implementation: {@link UnsafeProxy}</li>
 * <li>A "safe" implementation: {@link ReflectiveProxy}</li>
 * </ul>
 * <p>
 * Users nominate which implementation they prefer by referencing the
 * {@link #PROXY_OPTIMAL} or {@link #PROXY_SAFE} field when invoking
 * {@link Env#create(org.lmdbjava.BufferProxy)}.
 */
public final class ByteBufferProxy {

  /**
   * The fastest {@link ByteBuffer} proxy that is available on this platform.
   * This will always be the same instance as {@link #PROXY_SAFE} if the
   * {@link UnsafeAccess#DISABLE_UNSAFE_PROP} has been set to <code>true</code>
   * and/or {@link UnsafeAccess} is unavailable. Guaranteed to never be null.
   */
  public static final BufferProxy<ByteBuffer> PROXY_OPTIMAL;

  /**
   * The safe, reflective {@link ByteBuffer} proxy for this system. Guaranteed
   * to never be null.
   */
  public static final BufferProxy<ByteBuffer> PROXY_SAFE;

  /**
   * A thread-safe pool for a given length. If the buffer found is valid (ie not
   * of a negative length) then that buffer is used. If no valid buffer is
   * found, a new buffer is created.
   */
  private static final ThreadLocal<ArrayDeque<ByteBuffer>> BUFFERS
      = withInitial(() -> new ArrayDeque<>(16));

  private static final String FIELD_NAME_ADDRESS = "address";
  private static final String FIELD_NAME_CAPACITY = "capacity";

  static {
    PROXY_SAFE = new ReflectiveProxy();
    PROXY_OPTIMAL = getProxyOptimal();
  }

  /**
   * Convenience method to copy the passed {@link ByteBuffer} into a byte
   * array.This method is not optimized and use is discouraged (use a proper
   * {@link BufferProxy} instead).
   *
   * @param buffer to copy into a byte array
   * @return a byte array of the same length as the passed buffer's capacity
   */
  public static byte[] array(final ByteBuffer buffer) {
    requireNonNull(buffer, "A non-null input ByteArray is required");
    final byte[] dest = new byte[buffer.capacity()];
    buffer.get(dest);
    return dest;
  }

  /**
   * Convenience method to create a direct {@link ByteBuffer} and copy the
   * passed byte array into it. This method is not optimized and use is
   * discouraged (use a proper {@link BufferProxy} instead).
   *
   * @param src to copy into a byte buffer
   * @return a byte buffer that contains the passed bytes
   */
  public static ByteBuffer buffer(final byte[] src) {
    requireNonNull(src, "A non-null input byte[] is required");
    final ByteBuffer buff = allocateDirect(src.length);
    buff.put(src);
    return buff;
  }

  private static long address(final ByteBuffer buffer) {
    if (SHOULD_CHECK) {
      if (!buffer.isDirect()) {
        throw new BufferMustBeDirectException();
      }
    }
    return ((sun.nio.ch.DirectBuffer) buffer).address();
  }

  private static BufferProxy<ByteBuffer> getProxyOptimal() {
    try {
      return new UnsafeProxy();
    } catch (Throwable e) {
      return PROXY_SAFE;
    }
  }

  static Field findField(final Class<?> c, final String name) {
    Class<?> clazz = c;
    do {
      try {
        final Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    } while (clazz != null);
    throw new RuntimeException(name + " not found");
  }

  private ByteBufferProxy() {
  }

  /**
   * The buffer must be a direct buffer (not heap allocated).
   */
  public static final class BufferMustBeDirectException extends LmdbException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance.
     */
    public BufferMustBeDirectException() {
      super("The buffer must be a direct buffer (not heap allocated");
    }
  }

  /**
   * A proxy that uses Java reflection to modify byte buffer fields, and
   * official JNR-FFF methods to manipulate native pointers.
   */
  private static final class ReflectiveProxy extends BufferProxy<ByteBuffer> {

    private static final Field ADDRESS_FIELD;
    private static final Field CAPACITY_FIELD;

    static {
      ADDRESS_FIELD = findField(Buffer.class, FIELD_NAME_ADDRESS);
      CAPACITY_FIELD = findField(Buffer.class, FIELD_NAME_CAPACITY);
    }

    @Override
    protected ByteBuffer allocate() {
      final ArrayDeque<ByteBuffer> queue = BUFFERS.get();
      final ByteBuffer buffer = queue.poll();

      if (buffer != null && buffer.capacity() >= 0) {
        return buffer;
      } else {
        final ByteBuffer bb = allocateDirect(0);
        return bb;
      }
    }

    @Override
    protected void deallocate(final ByteBuffer buff) {
      final ArrayDeque<ByteBuffer> queue = BUFFERS.get();
      queue.offer(buff);
    }

    @Override
    protected void in(final ByteBuffer buffer, final Pointer ptr,
                      final long ptrAddr) {
      ptr.putLong(STRUCT_FIELD_OFFSET_SIZE, buffer.capacity());
      ptr.putLong(STRUCT_FIELD_OFFSET_DATA, address(buffer));
    }

    @Override
    protected void in(final ByteBuffer buffer, final int size, final Pointer ptr,
                      final long ptrAddr) {
      ptr.putLong(STRUCT_FIELD_OFFSET_SIZE, size);
      ptr.putLong(STRUCT_FIELD_OFFSET_DATA, address(buffer));
    }

    @Override
    protected void out(final ByteBuffer buffer, final Pointer ptr,
                       final long ptrAddr) {
      final long addr = ptr.getLong(STRUCT_FIELD_OFFSET_DATA);
      final long size = ptr.getLong(STRUCT_FIELD_OFFSET_SIZE);
      try {
        ADDRESS_FIELD.set(buffer, addr);
        CAPACITY_FIELD.set(buffer, (int) size);
      } catch (IllegalArgumentException | IllegalAccessException ex) {
        throw new RuntimeException("Cannot modify buffer", ex);
      }
      buffer.clear();
    }

  }

  /**
   * A proxy that uses Java's "unsafe" class to directly manipulate byte buffer
   * fields and JNR-FFF allocated memory pointers.
   */
  private static final class UnsafeProxy extends BufferProxy<ByteBuffer> {

    private static final long ADDRESS_OFFSET;
    private static final long CAPACITY_OFFSET;

    static {
      try {
        final Field address = findField(Buffer.class, FIELD_NAME_ADDRESS);
        final Field capacity = findField(Buffer.class, FIELD_NAME_CAPACITY);
        ADDRESS_OFFSET = UNSAFE.objectFieldOffset(address);
        CAPACITY_OFFSET = UNSAFE.objectFieldOffset(capacity);
      } catch (SecurityException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected ByteBuffer allocate() {
      final ArrayDeque<ByteBuffer> queue = BUFFERS.get();
      final ByteBuffer buffer = queue.poll();

      if (buffer != null && buffer.capacity() >= 0) {
        return buffer;
      } else {
        final ByteBuffer bb = allocateDirect(0);
        return bb;
      }
    }

    @Override
    protected void deallocate(final ByteBuffer buff) {
      final ArrayDeque<ByteBuffer> queue = BUFFERS.get();
      queue.offer(buff);
    }

    @Override
    protected void in(final ByteBuffer buffer, final Pointer ptr,
                      final long ptrAddr) {
      UNSAFE.putLong(ptrAddr + STRUCT_FIELD_OFFSET_SIZE, buffer.capacity());
      UNSAFE.putLong(ptrAddr + STRUCT_FIELD_OFFSET_DATA, address(buffer));
    }

    @Override
    protected void in(final ByteBuffer buffer, final int size, final Pointer ptr,
                      final long ptrAddr) {
      UNSAFE.putLong(ptrAddr + STRUCT_FIELD_OFFSET_SIZE, size);
      UNSAFE.putLong(ptrAddr + STRUCT_FIELD_OFFSET_DATA, address(buffer));
    }

    @Override
    protected void out(final ByteBuffer buffer, final Pointer ptr,
                       final long ptrAddr) {
      final long addr = UNSAFE.getLong(ptrAddr + STRUCT_FIELD_OFFSET_DATA);
      final long size = UNSAFE.getLong(ptrAddr + STRUCT_FIELD_OFFSET_SIZE);
      UNSAFE.putLong(buffer, ADDRESS_OFFSET, addr);
      UNSAFE.putInt(buffer, CAPACITY_OFFSET, (int) size);
      buffer.clear();
    }
  }
}
