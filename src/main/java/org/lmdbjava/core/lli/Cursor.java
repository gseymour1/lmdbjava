package org.lmdbjava.core.lli;

import jnr.ffi.Pointer;
import org.lmdbjava.core.lli.Library.MDB_val;
import org.lmdbjava.core.lli.exceptions.LmdbNativeException;

import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;
import static org.lmdbjava.core.lli.Library.lib;
import static org.lmdbjava.core.lli.Library.runtime;
import static org.lmdbjava.core.lli.MemoryAccess.createVal;
import static org.lmdbjava.core.lli.MemoryAccess.wrap;
import static org.lmdbjava.core.lli.exceptions.ResultCodeMapper.checkRc;


public class Cursor {
  private final Pointer ptr;
  private final boolean isReadOnly;
  private boolean closed;

  Cursor(Pointer ptr, boolean isReadOnly) {
    this.ptr = ptr;
    this.isReadOnly = isReadOnly;
  }

  public boolean isReadOnly() {
    return isReadOnly;
  }

  public void put(ByteBuffer key, ByteBuffer val)
    throws LmdbNativeException {
    put(key, val, PutFlags.ZERO);
  }

  public void put(ByteBuffer key, ByteBuffer val, PutFlags op)
    throws LmdbNativeException {
    requireNonNull(key);
    requireNonNull(val);
    requireNonNull(op);
    final MDB_val k = createVal(key);
    final MDB_val v = createVal(val);
    checkRc(lib.mdb_cursor_put(ptr, k, v, op.getMask()));
  }

  public void get(ByteBuffer key, ByteBuffer val, CursorOp op)
    throws LmdbNativeException {
    requireNonNull(key);
    requireNonNull(val);
    requireNonNull(op);
    if (closed) {
      throw new IllegalArgumentException("Cursor closed");
    }
    final MDB_val k;
    final MDB_val v = new MDB_val(runtime);
    // set operations 15, 16, 17
    if (op.getCode() >= 15) {
      k = createVal(key);
    } else {
      k = new MDB_val(runtime);
    }
    checkRc(lib.mdb_cursor_get(ptr, k, v, op.getCode()));
    wrap(key, k);
    wrap(val, v);
  }

  public void count() {

  }

  public void renew(Transaction tx) throws LmdbNativeException {
    checkRc(lib.mdb_cursor_renew(tx.ptr, ptr));
  }

  public void close() {
    if (!closed) {
      lib.mdb_cursor_close(ptr);
      closed = true;
    }
  }

}
