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

import jnr.constants.Constant;
import jnr.constants.ConstantSet;
import static jnr.constants.ConstantSet.getConstantSet;
import static org.lmdbjava.Cursor.FullException.MDB_CURSOR_FULL;
import static org.lmdbjava.Dbi.BadDbiException.MDB_BAD_DBI;
import static org.lmdbjava.Dbi.BadValueSizeException.MDB_BAD_VALSIZE;
import static org.lmdbjava.Dbi.DbFullException.MDB_DBS_FULL;
import static org.lmdbjava.Dbi.IncompatibleException.MDB_INCOMPATIBLE;
import static org.lmdbjava.Dbi.KeyExistsException.MDB_KEYEXIST;
import static org.lmdbjava.Dbi.KeyNotFoundException.MDB_NOTFOUND;
import static org.lmdbjava.Dbi.MapResizedException.MDB_MAP_RESIZED;
import static org.lmdbjava.Env.FileInvalidException.MDB_INVALID;
import static org.lmdbjava.Env.MapFullException.MDB_MAP_FULL;
import static org.lmdbjava.Env.ReadersFullException.MDB_READERS_FULL;
import static org.lmdbjava.Env.VersionMismatchException.MDB_VERSION_MISMATCH;
import static org.lmdbjava.LmdbNativeException.PageCorruptedException.MDB_CORRUPTED;
import static org.lmdbjava.LmdbNativeException.PageFullException.MDB_PAGE_FULL;
import static org.lmdbjava.LmdbNativeException.PageNotFoundException.MDB_PAGE_NOTFOUND;
import static org.lmdbjava.LmdbNativeException.PanicException.MDB_PANIC;
import static org.lmdbjava.LmdbNativeException.TlsFullException.MDB_TLS_FULL;
import org.lmdbjava.Txn.BadException;
import static org.lmdbjava.Txn.BadException.MDB_BAD_TXN;
import org.lmdbjava.Txn.BadReaderLockException;
import static org.lmdbjava.Txn.BadReaderLockException.MDB_BAD_RSLOT;
import org.lmdbjava.Txn.TxFullException;
import static org.lmdbjava.Txn.TxFullException.MDB_TXN_FULL;

/**
 * Maps a LMDB C result code to the equivalent Java exception.
 */
final class ResultCodeMapper {

  private static final ConstantSet CONSTANTS;
  private static final String POSIX_ERR_NO = "Errno";

  /**
   * Successful result
   */
  static final int MDB_SUCCESS = 0;

  static {
    CONSTANTS = getConstantSet(POSIX_ERR_NO);
  }

  /**
   * Checks the result code and raises an exception is not {@link #MDB_SUCCESS}.
   *
   * @param rc the LMDB result code
   * @throws LmdbNativeException the resolved exception
   */
  static void checkRc(final int rc) throws LmdbNativeException {
    if (rc == MDB_SUCCESS) {
      return;
    }

    final LmdbNativeException nativeException = rcException(rc);
    if (nativeException != null) {
      throw nativeException;
    }

    final Constant constant = CONSTANTS.getConstant(rc);
    if (constant == null) {
      throw new IllegalArgumentException("Unknown result code " + rc);
    }
    throw new LmdbNativeException.ConstantDerviedException(rc, constant.name());
  }

  /**
   * Returns the appropriate exception for a given result code.
   * <p>
   * The passed result code must be a value other than {@link #MDB_SUCCESS}.
   * Passing {@link #MDB_SUCCESS} will raise an exception.
   * <p>
   * If the passed result code cannot be mapped to an LMDB exception, null is
   * returned.
   *
   * @param rc the non-zero LMDB result code
   * @return the resolved exception (may be null if not an LMDB result code)
   */
  static LmdbNativeException rcException(final int rc) throws
      IllegalArgumentException {
    if (rc == MDB_SUCCESS) {
      throw new IllegalArgumentException("Non-zero value required");
    }

    switch (rc) {
      case MDB_BAD_DBI:
        return new Dbi.BadDbiException();
      case MDB_BAD_RSLOT:
        return new BadReaderLockException();
      case MDB_BAD_TXN:
        return new BadException();
      case MDB_BAD_VALSIZE:
        return new Dbi.BadValueSizeException();
      case MDB_CORRUPTED:
        return new LmdbNativeException.PageCorruptedException();
      case MDB_CURSOR_FULL:
        return new Cursor.FullException();
      case MDB_DBS_FULL:
        return new Dbi.DbFullException();
      case MDB_INCOMPATIBLE:
        return new Dbi.IncompatibleException();
      case MDB_INVALID:
        return new Env.FileInvalidException();
      case MDB_KEYEXIST:
        return new Dbi.KeyExistsException();
      case MDB_MAP_FULL:
        return new Env.MapFullException();
      case MDB_MAP_RESIZED:
        return new Dbi.MapResizedException();
      case MDB_NOTFOUND:
        return new Dbi.KeyNotFoundException();
      case MDB_PAGE_FULL:
        return new LmdbNativeException.PageFullException();
      case MDB_PAGE_NOTFOUND:
        return new LmdbNativeException.PageNotFoundException();
      case MDB_PANIC:
        return new LmdbNativeException.PanicException();
      case MDB_READERS_FULL:
        return new Env.ReadersFullException();
      case MDB_TLS_FULL:
        return new LmdbNativeException.TlsFullException();
      case MDB_TXN_FULL:
        return new TxFullException();
      case MDB_VERSION_MISMATCH:
        return new Env.VersionMismatchException();
    }
    return null;
  }

  private ResultCodeMapper() {
  }
}
