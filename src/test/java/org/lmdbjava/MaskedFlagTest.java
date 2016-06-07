package org.lmdbjava;

import java.util.HashSet;
import java.util.Set;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Test;
import static org.lmdbjava.EnvFlags.MDB_FIXEDMAP;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;
import static org.lmdbjava.MaskedFlag.mask;

public class MaskedFlagTest {

  @Test
  public void masking() {
    final Set<EnvFlags> flags = new HashSet<>();
    assertThat(mask(flags), is(0));
    flags.add(MDB_NOSYNC);
    assertThat(mask(flags), is(MDB_NOSYNC.getMask()));
    flags.add(MDB_FIXEDMAP);
    final int expected = MDB_NOSYNC.getMask() + MDB_FIXEDMAP.getMask();
    assertThat(mask(flags), is(expected));
  }
}
