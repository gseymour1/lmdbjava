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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import static java.util.Arrays.asList;
import java.util.LinkedList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.lmdbjava.CursorIterator.IteratorType.BACKWARD;
import static org.lmdbjava.CursorIterator.IteratorType.FORWARD;
import org.lmdbjava.CursorIterator.KeyVal;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.open;
import static org.lmdbjava.EnvFlags.MDB_NOSUBDIR;
import static org.lmdbjava.PutFlags.MDB_NOOVERWRITE;
import static org.lmdbjava.TestUtils.DB_1;
import static org.lmdbjava.TestUtils.bb;

public class CursorIteratorTest {

  @Rule
  public final TemporaryFolder tmp = new TemporaryFolder();
  private Dbi<ByteBuffer> db;
  private Env<ByteBuffer> env;
  private LinkedList<Integer> list;

  @Test
  public void backward() {
    try (final Txn<ByteBuffer> txn = env.txnRead();
         CursorIterator<ByteBuffer> c = db.iterate(txn, BACKWARD)) {
      for (KeyVal<ByteBuffer> kv : c.iterable()) {
        assertThat(kv.val.getInt(), is(list.pollLast()));
        assertThat(kv.key.getInt(), is(list.pollLast()));
      }
    }
  }

  @Test
  public void backwardSeek() {
    ByteBuffer key = bb(5);
    list.pollLast();
    list.pollLast();
    try (final Txn<ByteBuffer> txn = env.txnRead();
         CursorIterator<ByteBuffer> c = db.iterate(txn, key, BACKWARD)) {
      for (KeyVal<ByteBuffer> kv : c.iterable()) {
        assertThat(kv.val.getInt(), is(list.pollLast()));
        assertThat(kv.key.getInt(), is(list.pollLast()));
      }
    }
  }

  @Before
  public void before() throws IOException {
    final File path = tmp.newFile();
    env = open(path, 10, MDB_NOSUBDIR);
    db = env.openDbi(DB_1, MDB_CREATE);
    list = new LinkedList<>();
    list.addAll(asList(1, 2, 3, 4, 5, 6, 7, 8));
    try (final Txn<ByteBuffer> txn = env.txnWrite()) {
      final Cursor<ByteBuffer> c = db.openCursor(txn);
      c.put(bb(1), bb(2), MDB_NOOVERWRITE);
      c.put(bb(3), bb(4));
      c.put(bb(5), bb(6));
      c.put(bb(7), bb(8));
      txn.commit();
    }
  }

  @Test
  public void forward() {
    try (final Txn<ByteBuffer> txn = env.txnRead();
         CursorIterator<ByteBuffer> c = db.iterate(txn, FORWARD)) {
      for (KeyVal<ByteBuffer> kv : c.iterable()) {
        assertThat(kv.key.getInt(), is(list.pollFirst()));
        assertThat(kv.val.getInt(), is(list.pollFirst()));
      }
    }
  }

  @Test
  public void forwardSeek() {
    ByteBuffer key = bb(3);
    list.pollFirst();
    list.pollFirst();

    try (final Txn<ByteBuffer> txn = env.txnRead();
         CursorIterator<ByteBuffer> c = db.iterate(txn, key, FORWARD)) {
      for (KeyVal<ByteBuffer> kv : c.iterable()) {
        assertThat(kv.key.getInt(), is(list.pollFirst()));
        assertThat(kv.val.getInt(), is(list.pollFirst()));
      }
    }
  }

  @Test
  public void iterate() {
    try (final Txn<ByteBuffer> txn = env.txnRead();
         CursorIterator<ByteBuffer> c = db.iterate(txn)) {
      for (KeyVal<ByteBuffer> kv : c.iterable()) {
        assertThat(kv.key.getInt(), is(list.pollFirst()));
        assertThat(kv.val.getInt(), is(list.pollFirst()));
      }
    }
  }

}
