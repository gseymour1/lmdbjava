package org.lmdbjava;

import static java.util.Objects.requireNonNull;
import static jnr.ffi.Memory.allocateDirect;
import static jnr.ffi.NativeType.ADDRESS;

import jnr.ffi.Pointer;

import static org.lmdbjava.Library.lib;
import static org.lmdbjava.Library.runtime;
import static org.lmdbjava.MaskedFlag.isSet;
import static org.lmdbjava.MaskedFlag.mask;
import static org.lmdbjava.ResultCodeMapper.checkRc;
import static org.lmdbjava.TxnFlags.MDB_RDONLY;

/**
 * LMDB transaction.
 */
public final class Txn implements AutoCloseable {

  private boolean committed;
  private final boolean readOnly;
  private boolean reset = false;
  final Env env;
  final Txn parent;
  final Pointer ptr;

  /**
   * Create a transaction handle.
   * <p>
   * An transaction can be read-only or read-write, based on the passed
   * transaction flags.
   * <p>
   * The environment must be open at the time of calling this constructor.
   * <p>
   * A transaction must be finalised by calling {@link #commit()} or
   * {@link #abort()}.
   *
   * @param env    the owning environment (required)
   * @param parent parent transaction (may be null if no parent)
   * @param flags  applicable flags (eg for a reusable, read-only transaction)
   * @throws NotOpenException    if the environment is not currently open
   * @throws LmdbNativeException if a native C error occurred
   */
  public Txn(final Env env, final Txn parent,
             final TxnFlags... flags) throws NotOpenException,
    LmdbNativeException {
    requireNonNull(env);
    if (!env.isOpen() || env.isClosed()) {
      throw new NotOpenException(Env.class.getSimpleName());
    }
    this.env = env;
    final int flagsMask = mask(flags);
    this.readOnly = isSet(flagsMask, MDB_RDONLY);
    this.parent = parent;
    final Pointer txnPtr = allocateDirect(runtime, ADDRESS);
    final Pointer txnParentPtr = parent == null ? null : parent.ptr;
    checkRc(lib.mdb_txn_begin(env.ptr, txnParentPtr, flagsMask, txnPtr));
    ptr = txnPtr.getPointer(0);
  }

  /**
   * Create a write transaction handle without a parent transaction.
   *
   * @param env the owning environment (required)
   * @throws NotOpenException    if the environment is not currently open
   * @throws LmdbNativeException if a native C error occurred
   */
  public Txn(final Env env)
    throws NotOpenException, LmdbNativeException {
    this(env, null, (TxnFlags[]) null);
  }

  /**
   * Create a read or write transaction handle without a parent transaction.
   *
   * @param env the owning environment (required)
   * @param flags applicable flags (eg for a reusable, read-only transaction)
   * @throws NotOpenException    if the environment is not currently open
   * @throws LmdbNativeException if a native C error occurred
   */
  public Txn(final Env env, TxnFlags... flags)
    throws NotOpenException, LmdbNativeException {
    this(env, null, flags);
  }

  /**
   * Aborts this transaction.
   *
   * @throws TxnAlreadyCommittedException if already committed
   */
  public void abort() throws TxnAlreadyCommittedException {
    if (committed) {
      throw new TxnAlreadyCommittedException();
    }
    lib.mdb_txn_abort(ptr);
    this.committed = true;
  }

  /**
   * Closes this transaction by aborting if not already committed.
   */
  @Override
  public void close() {
    if (committed) {
      return;
    }
    lib.mdb_txn_abort(ptr);
    this.committed = true;
  }

  /**
   * Commits this transaction.
   *
   * @throws TxnAlreadyCommittedException if already committed
   * @throws LmdbNativeException       if a native C error occurred
   */
  public void commit() throws TxnAlreadyCommittedException, LmdbNativeException {
    if (committed) {
      throw new TxnAlreadyCommittedException();
    }
    checkRc(lib.mdb_txn_commit(ptr));
    this.committed = true;
  }

  /**
   * Return the transaction's ID.
   *
   * @return A transaction ID, valid if input is an active transaction.
   */
  public long getId() {
    return lib.mdb_txn_id(ptr);
  }

  /**
   * Obtains this transaction's parent.
   *
   * @return the parent transaction (may be null)
   */
  public Txn getParent() {
    return parent;
  }

  /**
   * Whether this transaction has been committed.
   *
   * @return true if committed
   */
  public boolean isCommitted() {
    return committed;
  }

  /**
   * Whether this transaction is read-only.
   *
   * @return if read-only
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Whether this transaction has been {@link #reset()}.
   *
   * @return if reset
   */
  public boolean isReset() {
    return reset;
  }

  /**
   * Renews a read-only transaction previously released by {@link #reset()}.
   *
   * @throws TxnHasNotBeenResetException if reset not called
   * @throws LmdbNativeException                 if a native C error occurred
   */
  public void renew() throws TxnHasNotBeenResetException,
    LmdbNativeException {
    if (!reset) {
      throw new TxnHasNotBeenResetException();
    }
    reset = false;
    checkRc(lib.mdb_txn_renew(ptr));
  }

  /**
   * Aborts this read-only transaction and resets the transaction handle so it
   * can be reused upon calling {@link #renew()}.
   *
   * @throws ReadOnlyTransactionRequiredException if a read-write transaction
   * @throws TxnAlreadyResetException     if reset already performed
   */
  public void reset() throws ReadOnlyTransactionRequiredException,
    TxnAlreadyResetException {
    if (!isReadOnly()) {
      throw new ReadOnlyTransactionRequiredException();
    }
    if (reset) {
      throw new TxnAlreadyResetException();
    }
    lib.mdb_txn_reset(ptr);
    reset = true;
  }

}
