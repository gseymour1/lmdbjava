package org.lmdbjava;

import java.io.File;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.lmdbjava.EnvFlags.MDB_NOSUBDIR;
import static org.lmdbjava.TestUtils.POSIX_MODE;

public class EnvTest {

  @Rule
  public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void canCloseBeforeOpen() throws Exception {
    final Env env = new Env();
    env.close();
    assertThat(env.isClosed(), is(true));
  }

  @Test(expected = AlreadyOpenException.class)
  public void cannotOpenTwice() throws Exception {
    final Env e = new Env();
    final File path = tmp.newFile();
    e.open(path, POSIX_MODE, MDB_NOSUBDIR);
    e.open(path, POSIX_MODE, MDB_NOSUBDIR); // error
  }

  @Test(expected = AlreadyClosedException.class)
  public void cannotSetMapSizeOnceClosed() throws Exception {
    final Env env = new Env();
    env.close();
    env.setMapSize(1);
  }

  @Test(expected = AlreadyOpenException.class)
  public void cannotSetMapSizeOnceOpen() throws Exception {
    final Env env = new Env();
    final File path = tmp.newFile();
    env.open(path, POSIX_MODE, MDB_NOSUBDIR);
    env.setMapSize(1);
  }

  @Test(expected = AlreadyClosedException.class)
  public void cannotSetMaxDbsOnceClosed() throws Exception {
    final Env env = new Env();
    env.close();
    env.setMaxDbs(1);
  }

  @Test(expected = AlreadyOpenException.class)
  public void cannotSetMaxDbsOnceOpen() throws Exception {
    final Env env = new Env();
    final File path = tmp.newFile();
    env.open(path, POSIX_MODE, MDB_NOSUBDIR);
    env.setMaxDbs(1);
  }

  @Test(expected = AlreadyClosedException.class)
  public void cannotSetMaxReadersOnceClosed() throws Exception {
    final Env env = new Env();
    env.close();
    env.setMaxReaders(1);
  }

  @Test(expected = AlreadyOpenException.class)
  public void cannotSetMaxReadersOnceOpen() throws Exception {
    final Env env = new Env();
    final File path = tmp.newFile();
    env.open(path, POSIX_MODE, MDB_NOSUBDIR);
    env.setMaxReaders(1);
  }

  @Test
  public void createAsDirectory() throws Exception {
    final Env env = new Env();
    assertThat(env, is(notNullValue()));
    assertThat(env.isOpen(), is(false));
    assertThat(env.isClosed(), is(false));

    final File path = tmp.newFolder();
    env.open(path, POSIX_MODE);
    assertThat(env.isOpen(), is(true));
    assertThat(path.isDirectory(), is(true));
    env.close();
    assertThat(env.isClosed(), is(true));
    env.close(); // safe to repeat
  }

  @Test
  public void createAsFile() throws Exception {
    try (Env env = new Env()) {
      assertThat(env, is(notNullValue()));
      assertThat(env.isOpen(), is(false));
      final File path = tmp.newFile();
      env.setMapSize(1_024 * 1_024);
      env.setMaxDbs(1);
      env.setMaxReaders(1);
      env.open(path, POSIX_MODE, MDB_NOSUBDIR);
      assertThat(env.isOpen(), is(true));
      assertThat(path.isFile(), is(true));
    }
  }

  @Test
  public void info() throws Exception {
    final Env env = new Env();
    final File path = tmp.newFile();
    env.open(path, POSIX_MODE, MDB_NOSUBDIR);
    EnvInfo info = env.info();
    assertThat(info, is(notNullValue()));
  }

  @Test
  public void stats() throws Exception {
    final Env env = new Env();
    final File path = tmp.newFile();
    env.open(path, POSIX_MODE, MDB_NOSUBDIR);
    EnvStat stat = env.stat();
    assertThat(stat, is(notNullValue()));
    assertThat(stat.branchPages, is(0L));
    assertThat(stat.depth, is(0));
    assertThat(stat.entries, is(4_096L));
    assertThat(stat.leafPages, is(0L));
    assertThat(stat.overflowPages, is(0L));
    assertThat(stat.pageSize, is(4_096));
  }
}
