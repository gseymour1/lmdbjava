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

import java.nio.ByteBuffer;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Test;

import static org.lmdbjava.ByteBufferProxy.PROXY_SAFE;
import static org.lmdbjava.TestUtils.invokePrivateConstructor;
import static org.lmdbjava.UnsafeAccess.ALLOW_UNSAFE;

public class ByteBufferProxyTest {

  @Test
  public void coverPrivateConstructor() throws Exception {
    invokePrivateConstructor(ByteBufferProxy.class);
  }

  @Test
  public void safeCanBeForced() throws Exception {
    final BufferProxy<ByteBuffer> v = PROXY_SAFE;
    assertThat(v, is(notNullValue()));
  }

  @Test
  public void unsafeIsDefault() throws Exception {
    assertThat(ALLOW_UNSAFE, is(true));
    final BufferProxy<ByteBuffer> v = PROXY_SAFE;
    assertThat(v, is(notNullValue()));
  }

}
