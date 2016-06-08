package org.lmdbjava;

/**
 * Invalid reuse of reader locktable slot.
 */
public final class TxnBadReaderLockTableSlotException extends LmdbNativeException {

  private static final long serialVersionUID = 1L;
  static final int MDB_BAD_RSLOT = -30_783;

  TxnBadReaderLockTableSlotException() {
    super(MDB_BAD_RSLOT, "Invalid reuse of reader locktable slot");
  }
}
