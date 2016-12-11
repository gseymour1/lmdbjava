/*-
 * #%L
 * LmdbJava
 * %%
 * Copyright (C) 2016 The LmdbJava Open Source Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.lmdbjava;

import static com.jakewharton.byteunits.BinaryByteUnit.KIBIBYTES;
import java.io.File;
import java.io.IOException;
import static java.lang.Long.BYTES;
import static java.lang.Long.MIN_VALUE;
import java.nio.ByteBuffer;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.lmdbjava.ByteBufferProxy.PROXY_OPTIMAL;
import org.lmdbjava.Cursor.ClosedException;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;
import static org.lmdbjava.Env.create;
import static org.lmdbjava.EnvFlags.MDB_NOSUBDIR;
import static org.lmdbjava.PutFlags.MDB_APPENDDUP;
import static org.lmdbjava.PutFlags.MDB_NOOVERWRITE;
import static org.lmdbjava.SeekOp.MDB_FIRST;
import static org.lmdbjava.TestUtils.DB_1;
import static org.lmdbjava.TestUtils.POSIX_MODE;
import static org.lmdbjava.TestUtils.bb;

import org.lmdbjava.Txn.NotReadyException;
import org.lmdbjava.Txn.ReadOnlyRequiredException;

/**
 * Test {@link Cursor}.
 */
public final class CursorTest {

  @Rule
  public final TemporaryFolder tmp = new TemporaryFolder();

  @Test(expected = ClosedException.class)
  public void closedCursorRejectsSubsequentGets() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.close();
      c.seek(MDB_FIRST);
    }
  }

  @Test
  public void count() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE, MDB_DUPSORT);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.put(bb(1), bb(2), MDB_APPENDDUP);
      assertThat(c.count(), is(1L));
      c.put(bb(1), bb(4), MDB_APPENDDUP);
      c.put(bb(1), bb(6), MDB_APPENDDUP);
      assertThat(c.count(), is(3L));
      c.put(bb(2), bb(1), MDB_APPENDDUP);
      c.put(bb(2), bb(2), MDB_APPENDDUP);
      assertThat(c.count(), is(2L));
    }
  }

  @Test(expected = NotReadyException.class)
  public void cursorCannotCloseIfTransactionCommitted() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE, MDB_DUPSORT);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      try (Cursor<ByteBuffer> c = db.openCursor(txn);) {
        c.put(bb(1), bb(2), MDB_APPENDDUP);
        assertThat(c.count(), is(1L));
        c.put(bb(1), bb(4), MDB_APPENDDUP);
        assertThat(c.count(), is(2L));
        txn.commit();
      }
    }
  }

  @Test
  public void cursorFirstLastNextPrev() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      // populate data
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.put(bb(1), bb(2), MDB_NOOVERWRITE);
      c.put(bb(3), bb(4));
      c.put(bb(5), bb(6));
      c.put(bb(7), bb(8));

      assertThat(c.first(), is(true));
      assertThat(txn.key().getInt(0), is(1));
      assertThat(txn.val().getInt(0), is(2));

      assertThat(c.last(), is(true));
      assertThat(txn.key().getInt(0), is(7));
      assertThat(txn.val().getInt(0), is(8));

      assertThat(c.prev(), is(true));
      assertThat(txn.key().getInt(0), is(5));
      assertThat(txn.val().getInt(0), is(6));

      assertThat(c.first(), is(true));
      assertThat(c.next(), is(true));
      assertThat(txn.key().getInt(0), is(3));
      assertThat(txn.val().getInt(0), is(4));
    }
  }

  @Test
  public void delete() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE, MDB_DUPSORT);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.put(bb(1), bb(2), MDB_NOOVERWRITE);
      c.put(bb(3), bb(4));
      assertThat(c.seek(MDB_FIRST), is(true));
      assertThat(txn.key().getInt(), is(1));
      assertThat(txn.val().getInt(), is(2));
      c.delete();
      assertThat(c.seek(MDB_FIRST), is(true));
      assertThat(txn.key().getInt(), is(3));
      assertThat(txn.val().getInt(), is(4));
      c.delete();
      assertThat(c.seek(MDB_FIRST), is(false));
    }
  }

  @Test
  public void renewTxRo() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE);

    final Cursor<ByteBuffer> c;
    try (Txn<ByteBuffer> txn = env.txnRead()) {
      c = db.openCursor(txn);
      txn.commit();
    }

    try (Txn<ByteBuffer> txn = env.txnRead()) {
      c.renew(txn);
      txn.commit();
    }
  }

  @Test(expected = ReadOnlyRequiredException.class)
  public void renewTxRw() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      assertThat(txn.isReadOnly(), is(false));

      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.renew(txn);
    }
  }

  @Test
  public void repeatedCloseCausesNotError() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE, MDB_DUPSORT);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.close();
      c.close();
    }
  }

  @Test
  public void reserve() {
    final Env<ByteBuffer> env = makeEnv(PROXY_OPTIMAL);
    final Dbi<ByteBuffer> db = env.openDbi(DB_1, MDB_CREATE);
    final ByteBuffer key = bb(5);
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      assertNull(db.get(txn, key));
      final ByteBuffer val = c.reserve(key, BYTES * 2);
      assertNotNull(db.get(txn, key));
      val.putLong(MIN_VALUE).flip();
      txn.commit();
    }
    try (Txn<ByteBuffer> txn = env.txnWrite()) {
      final ByteBuffer val = db.get(txn, key);
      assertThat(val.capacity(), is(BYTES * 2));
      assertThat(val.getLong(), is(MIN_VALUE));
    }
  }

  private <T> Env<T> makeEnv(final BufferProxy<T> proxy) {
    try {
      final File path = tmp.newFile();
      final Env<T> env = create(proxy)
          .setMapSize(KIBIBYTES.toBytes(1_024))
          .setMaxReaders(1)
          .setMaxDbs(1)
          .open(path, POSIX_MODE, MDB_NOSUBDIR);
      return env;
    } catch (final IOException e) {
      throw new LmdbException("IO failure", e);
    }
  }

}
