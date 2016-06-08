package org.lmdbjava;

import java.nio.ByteBuffer;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.Objects.requireNonNull;
import jnr.ffi.byref.IntByReference;
import jnr.ffi.byref.PointerByReference;
import org.lmdbjava.Library.MDB_val;
import static org.lmdbjava.Library.lib;
import static org.lmdbjava.Library.runtime;
import static org.lmdbjava.MaskedFlag.mask;
import static org.lmdbjava.ResultCodeMapper.checkRc;
import static org.lmdbjava.TransactionFlags.MDB_RDONLY;
import static org.lmdbjava.ValueBuffers.createVal;
import static org.lmdbjava.ValueBuffers.wrap;

/**
 * LMDB Database.
 */
public final class Database {

  private final String name;
  final int dbi;
  final Env env;

  /**
   * Create and open an LMDB Database (dbi) handle.
   * <p>
   * The passed transaction will automatically commit and the database handle
   * will become available to other transactions.
   *
   * @param tx    transaction to open and commit this database within (required)
   * @param name  name of the database (or null if no name is required)
   * @param flags to open the database with
   * @throws AlreadyCommittedException if already committed
   * @throws LmdbNativeException       if a native C error occurred
   */
  public Database(Transaction tx, String name, DatabaseFlags... flags)
      throws AlreadyCommittedException, LmdbNativeException {
    requireNonNull(tx);
    if (tx.isCommitted()) {
      throw new AlreadyCommittedException();
    }
    this.env = tx.env;
    this.name = name;
    final int flagsMask = mask(flags);
    final IntByReference dbiPtr = new IntByReference();
    checkRc(lib.mdb_dbi_open(tx.ptr, name, flagsMask, dbiPtr));
    dbi = dbiPtr.intValue();
  }

  public void delete(ByteBuffer key) throws
      AlreadyCommittedException, LmdbNativeException, NotOpenException {
    try (Transaction tx = new Transaction(env, null)) {
      delete(tx, key);
      tx.commit();
    }
  }

  public void delete(Transaction tx, ByteBuffer key) throws
      AlreadyCommittedException, LmdbNativeException {

    final MDB_val k = createVal(key);

    checkRc(lib.mdb_del(tx.ptr, dbi, k, null));
  }

  public ByteBuffer get(ByteBuffer key) throws
      AlreadyCommittedException, LmdbNativeException, NotOpenException {
    try (Transaction tx = new Transaction(env, null, MDB_RDONLY)) {
      return get(tx, key);
    }
  }

  public ByteBuffer get(Transaction tx, ByteBuffer key) throws
      AlreadyCommittedException, LmdbNativeException {
    assert key.isDirect();

    final MDB_val k = createVal(key);
    final MDB_val v = new MDB_val(runtime);

    checkRc(lib.mdb_get(tx.ptr, dbi, k, v));

    // inefficient as we create a BB
    ByteBuffer bb = allocateDirect(1).order(LITTLE_ENDIAN);
    wrap(bb, v);
    return bb;
  }

  /**
   * Obtains the name of this database.
   *
   * @return the name (may be null or empty)
   */
  public String getName() {
    return name;
  }

  public Cursor openCursor(Transaction tx) throws LmdbNativeException {
    PointerByReference ptr = new PointerByReference();
    checkRc(lib.mdb_cursor_open(tx.ptr, dbi, ptr));
    return new Cursor(ptr.getValue(), tx);
  }

  public void put(ByteBuffer key, ByteBuffer val) throws
      AlreadyCommittedException, LmdbNativeException, NotOpenException {
    try (Transaction tx = new Transaction(env, null)) {
      put(tx, key, val);
      tx.commit();
    }
  }

  public void put(Transaction tx, ByteBuffer key, ByteBuffer val) throws
      AlreadyCommittedException, LmdbNativeException {

    final MDB_val k = createVal(key);
    final MDB_val v = createVal(val);

    checkRc(lib.mdb_put(tx.ptr, dbi, k, v, 0));
  }
}
