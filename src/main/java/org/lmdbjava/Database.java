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
import static org.lmdbjava.TxnFlags.MDB_RDONLY;
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
   * @throws TxnAlreadyCommittedException if already committed
   * @throws LmdbNativeException       if a native C error occurred
   */
  public Database(Txn tx, String name, DatabaseFlags... flags)
      throws TxnAlreadyCommittedException, LmdbNativeException {
    requireNonNull(tx);
    if (tx.isCommitted()) {
      throw new TxnAlreadyCommittedException();
    }
    this.env = tx.env;
    this.name = name;
    final int flagsMask = mask(flags);
    final IntByReference dbiPtr = new IntByReference();
    checkRc(lib.mdb_dbi_open(tx.ptr, name, flagsMask, dbiPtr));
    dbi = dbiPtr.intValue();
  }

  /**
   * @see org.lmdbjava.Database#delete(Txn, ByteBuffer, ByteBuffer)
   */
  public void delete(ByteBuffer key) throws
    TxnAlreadyCommittedException, LmdbNativeException, NotOpenException {
    try (Txn tx = new Txn(env, null)) {
      delete(tx, key);
      tx.commit();
    }
  }

  /**
   * @see org.lmdbjava.Database#delete(Txn, ByteBuffer, ByteBuffer)
   */
  public void delete(Txn tx, ByteBuffer key) throws
    TxnAlreadyCommittedException, LmdbNativeException {
    delete(tx, key, null);
  }

  /**
   * <p>
   * Removes key/data pairs from the database.
   * </p>
   * If the database does not support sorted duplicate data items
   * ({@link org.lmdbjava.DatabaseFlags#MDB_DUPSORT}) the value
   * parameter is ignored.
   * If the database supports sorted duplicates and the data parameter
   * is NULL, all of the duplicate data items for the key will be
   * deleted. Otherwise, if the data parameter is non-NULL
   * only the matching data item will be deleted.
   * This function will return false if the specified key/data
   * pair is not in the database.
   *
   * @param tx Transaction handle.
   * @param key The key to delete from the database.
   * @param val The value to delete from the database
   * @return true if the key/value was deleted.
   */
  public void delete(Txn tx, ByteBuffer key, ByteBuffer val) throws
    TxnAlreadyCommittedException, LmdbNativeException {

    final MDB_val k = createVal(key);
    final MDB_val v = val == null ? null : createVal(key);

    checkRc(lib.mdb_del(tx.ptr, dbi, k, v));
  }

  /**
   * @see org.lmdbjava.Database#get(Txn, ByteBuffer)
   */
  public ByteBuffer get(ByteBuffer key) throws
    TxnAlreadyCommittedException, LmdbNativeException, NotOpenException {
    try (Txn tx = new Txn(env, MDB_RDONLY)) {
      return get(tx, key);
    }
  }

  /**
   * <p>
   *   Get items from a database.
   * </p>
   *
   * This function retrieves key/data pairs from the database. The address
   * and length of the data associated with the specified \b key are returned
   * in the structure to which \b data refers.
   * If the database supports duplicate keys ({@link org.lmdbjava.DatabaseFlags#MDB_DUPSORT})
   * then the first data item for the key will be returned. Retrieval of other
   * items requires the use of #mdb_cursor_get().
   *
   * @param tx transaction handle
   * @param key The key to search for in the database
   * @return A value placeholder for the memory address to be wrapped if found by key.
   */
  public ByteBuffer get(Txn tx, ByteBuffer key) throws
    TxnAlreadyCommittedException, LmdbNativeException {
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


  /**
   * <p>
   *  Create a cursor handle.
   * </p>
   *
   * A cursor is associated with a specific transaction and database.
   * A cursor cannot be used when its database handle is closed.  Nor
   * when its transaction has ended, except with #mdb_cursor_renew().
   * It can be discarded with #mdb_cursor_close().
   * A cursor in a write-transaction can be closed before its transaction
   * ends, and will otherwise be closed when its transaction ends.
   * A cursor in a read-only transaction must be closed explicitly, before
   * or after its transaction ends. It can be reused with
   * #mdb_cursor_renew() before finally closing it.
   * @note Earlier documentation said that cursors in every transaction
   * were closed when the transaction committed or aborted.
   *
   * @param tx transaction handle
   * @return cursor handle
   */
  public Cursor openCursor(Txn tx) throws LmdbNativeException {
    PointerByReference ptr = new PointerByReference();
    checkRc(lib.mdb_cursor_open(tx.ptr, dbi, ptr));
    return new Cursor(ptr.getValue(), tx);
  }

  /**
   * @see org.lmdbjava.Database#put(Txn, ByteBuffer, ByteBuffer, DatabaseFlags...)
   */
  public void put(ByteBuffer key, ByteBuffer val) throws
    TxnAlreadyCommittedException, LmdbNativeException, NotOpenException {
    try (Txn tx = new Txn(env, null)) {
      put(tx, key, val);
      tx.commit();
    }
  }

  /**
   * <p>
   * Store items into a database.
   * </p>
   *
   * This function stores key/data pairs in the database. The default behavior
   * is to enter the new key/data pair, replacing any previously existing key
   * if duplicates are disallowed, or adding a duplicate data item if
   * duplicates are allowed ({@link org.lmdbjava.DatabaseFlags#MDB_DUPSORT}).
   *
   * @param tx transaction handle
   * @param key The key to store in the database
   * @param val The value to store in the database
   * @param flags Special options for this operation.
   *
   * @return the existing value if it was a dup insert attempt.
   */
  public void put(Txn tx, ByteBuffer key, ByteBuffer val, DatabaseFlags... flags) throws
    TxnAlreadyCommittedException, LmdbNativeException {

    final MDB_val k = createVal(key);
    final MDB_val v = createVal(val);
    int mask = MaskedFlag.mask(flags);
    checkRc(lib.mdb_put(tx.ptr, dbi, k, v, mask));
  }
}
