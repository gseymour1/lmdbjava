package org.lmdbjava;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.lmdbjava.DatabaseFlags.MDB_CREATE;
import static org.lmdbjava.DatabaseFlags.MDB_DUPSORT;
import static org.lmdbjava.EnvFlags.MDB_NOSUBDIR;
import static org.lmdbjava.TestUtils.DB_1;
import static org.lmdbjava.TestUtils.POSIX_MODE;
import static org.lmdbjava.TestUtils.createBb;

public class CursorTest {

  @Rule
  public final TemporaryFolder tmp = new TemporaryFolder();
  private Env env;
  private Transaction tx;
  private Database db;

  @Before
  public void before() throws Exception {
    env = new Env();
    final File path = tmp.newFile();

    final Set<EnvFlags> envFlags = new HashSet<>();
    envFlags.add(MDB_NOSUBDIR);

    env.setMapSize(1_024 * 1_024);
    env.setMaxDbs(1);
    env.setMaxReaders(1);
    env.open(path, envFlags, POSIX_MODE);
    tx = env.txnBeginReadWrite();
    Set<DatabaseFlags> dbFlags = new HashSet<>();
    dbFlags.add(MDB_CREATE);
    dbFlags.add(MDB_DUPSORT);
    db = tx.databaseOpen(DB_1, dbFlags);
  }

  @Test
  public void testCursorGet() throws Exception {
    Cursor cursor = db.openCursor(tx);
    cursor.put(createBb(1), createBb(2), PutFlags.MDB_NOOVERWRITE);
    cursor.put(createBb(3), createBb(4));

    ByteBuffer k = createBb();
    ByteBuffer v = createBb();

    cursor.get(k, v, CursorOp.MDB_FIRST);
    assertThat(k.getInt(), is(1));
    assertThat(v.getInt(), is(2));

    cursor.get(k, v, CursorOp.MDB_NEXT);
    assertThat(k.getInt(), is(3));
    assertThat(v.getInt(), is(4));

    cursor.get(k, v, CursorOp.MDB_PREV);
    assertThat(k.getInt(), is(1));
    assertThat(v.getInt(), is(2));

    cursor.get(k, v, CursorOp.MDB_LAST);
    assertThat(k.getInt(), is(3));
    assertThat(v.getInt(), is(4));

    tx.commit();
  }

  @Test
  public void testCursorCount() throws Exception {
    Cursor cursor = db.openCursor(tx);

    cursor.put(createBb(1), createBb(2), PutFlags.MDB_APPENDDUP);
    assertThat(cursor.count(), is(1L));

    cursor.put(createBb(1), createBb(4), PutFlags.MDB_APPENDDUP);
    assertThat(cursor.count(), is(2L));
    tx.commit();
  }

  @Test
  public void testCursorSet() throws Exception {
    Cursor cursor = db.openCursor(tx);
    cursor.put(createBb(1), createBb(2));
    cursor.put(createBb(3), createBb(4));
    cursor.put(createBb(5), createBb(6));

    ByteBuffer k = createBb(1);
    ByteBuffer v = createBb();

    cursor.get(k, v, CursorOp.MDB_SET);
    assertThat(k.getInt(), is(1));
    assertThat(v.getInt(), is(2));

    k = createBb(3);
    cursor.get(k, v, CursorOp.MDB_SET_KEY);
    assertThat(k.getInt(), is(3));
    assertThat(v.getInt(), is(4));

    k = createBb(5);
    cursor.get(k, v, CursorOp.MDB_SET_RANGE);
    assertThat(k.getInt(), is(5));
    assertThat(v.getInt(), is(6));

    k = createBb(0);
    cursor.get(k, v, CursorOp.MDB_SET_RANGE);
    assertThat(k.getInt(), is(1));
    assertThat(v.getInt(), is(2));

    tx.commit();
  }

  @Test
  public void testCursorDelete() throws Exception {
    Cursor cursor = db.openCursor(tx);
    cursor.put(createBb(1), createBb(2), PutFlags.MDB_NOOVERWRITE);
    cursor.put(createBb(3), createBb(4));

    ByteBuffer k = createBb(1);
    ByteBuffer v = createBb();

    cursor.get(k, v, CursorOp.MDB_FIRST);
    assertThat(k.getInt(), is(1));
    assertThat(v.getInt(), is(2));
    cursor.delete();

    cursor.get(k, v, CursorOp.MDB_FIRST);
    assertThat(k.getInt(), is(3));
    assertThat(v.getInt(), is(4));
    cursor.delete();

    try {
      cursor.get(k, v, CursorOp.MDB_FIRST);
      fail("should fail");
    } catch (NotFoundException e) {
    }
    tx.commit();
  }

  @Test(expected = IllegalArgumentException.class)
  public void closeCursor() throws LmdbNativeException, AlreadyCommittedException {
    Set<DatabaseFlags> dbFlags = new HashSet<>();
    dbFlags.add(MDB_CREATE);
    Database db = tx.databaseOpen(DB_1, dbFlags);
    Cursor cursor = db.openCursor(tx);
    cursor.close();
    ByteBuffer k = createBb(1);
    ByteBuffer v = createBb(1);
    cursor.get(k, v, CursorOp.MDB_FIRST);
  }
}
