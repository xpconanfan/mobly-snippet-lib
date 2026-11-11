/*
 * Copyright (C) 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.mobly.snippet.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.manager.SnippetManager;
import com.google.android.mobly.snippet.util.Log;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link MethodDescriptor}, focusing on {@link RpcDefault} type conversion. */
@RunWith(JUnit4.class)
public class MethodDescriptorTest {

    /** Test snippet exercising primitive and boxed {@link RpcDefault} parameters. */
    public static class DefaultParamsSnippet implements Snippet {
        @Rpc(description = "RPC with primitive default values.")
        public String primitiveDefaults(
                @RpcDefault("hello") String text,
                @RpcDefault("true") boolean flag,
                @RpcDefault("42") int count,
                @RpcDefault("9876543210") long timestamp) {
            return text + ":" + flag + ":" + count + ":" + timestamp;
        }

        @Rpc(description = "RPC with boxed default values.")
        public String boxedDefaults(
                @RpcDefault("FALSE") Boolean flag,
                @RpcDefault("-7") Integer count,
                @RpcDefault("0x10") Long hexLong) {
            return flag + ":" + count + ":" + hexLong;
        }

        @Rpc(description = "RPC with invalid primitive boolean default.")
        public boolean invalidBooleanDefault(@RpcDefault("not_a_bool") boolean flag) {
            return flag;
        }

        @Rpc(description = "RPC with invalid primitive int default.")
        public int invalidIntDefault(@RpcDefault("not_an_int") int count) {
            return count;
        }

        @Rpc(description = "RPC with invalid primitive long default.")
        public long invalidLongDefault(@RpcDefault("not_a_long") long value) {
            return value;
        }

        @Rpc(description = "RPC with unsupported default parameter type.")
        public String unsupportedDefault(@RpcDefault("val") Object unsupported) {
            return String.valueOf(unsupported);
        }

        @Override
        public void shutdown() {}
    }

    private SnippetManager mSnippetManager;
    private Map<String, MethodDescriptor> mDescriptors;

    @Before
    public void setUp() throws Exception {
        Log.apkLogTag = "MethodDescriptorTest";
        Constructor<SnippetManager> constructor =
                SnippetManager.class.getDeclaredConstructor(Collection.class);
        constructor.setAccessible(true);
        mSnippetManager =
                constructor.newInstance(Collections.singletonList(DefaultParamsSnippet.class));
        mDescriptors = new HashMap<>();
        for (MethodDescriptor descriptor :
                MethodDescriptor.collectFrom(DefaultParamsSnippet.class)) {
            mDescriptors.put(descriptor.getName(), descriptor);
        }
    }

    @Test
    public void invoke_primitiveRpcDefaults_usesAllDefaultsWhenOmitted() throws Throwable {
        MethodDescriptor descriptor = mDescriptors.get("primitiveDefaults");

        Object result = descriptor.invoke(mSnippetManager, new JSONArray());

        assertEquals("hello:true:42:9876543210", result);
    }

    @Test
    public void invoke_primitiveRpcDefaults_allowsPartialAndFullOverrides() throws Throwable {
        MethodDescriptor descriptor = mDescriptors.get("primitiveDefaults");

        Object partialOverride =
                descriptor.invoke(mSnippetManager, new JSONArray().put("custom").put(false));
        assertEquals("custom:false:42:9876543210", partialOverride);

        Object fullOverride =
                descriptor.invoke(
                        mSnippetManager,
                        new JSONArray().put("override").put(false).put(99).put(123L));
        assertEquals("override:false:99:123", fullOverride);
    }

    @Test
    public void invoke_boxedRpcDefaults_convertsValuesCorrectly() throws Throwable {
        MethodDescriptor descriptor = mDescriptors.get("boxedDefaults");

        Object result = descriptor.invoke(mSnippetManager, new JSONArray());

        assertEquals("false:-7:16", result);
    }

    @Test
    public void invoke_invalidPrimitiveDefaults_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mDescriptors
                                .get("invalidBooleanDefault")
                                .invoke(mSnippetManager, new JSONArray()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mDescriptors
                                .get("invalidIntDefault")
                                .invoke(mSnippetManager, new JSONArray()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mDescriptors
                                .get("invalidLongDefault")
                                .invoke(mSnippetManager, new JSONArray()));
    }

    @Test
    public void invoke_unsupportedDefaultType_throwsIllegalArgumentException() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mDescriptors
                                        .get("unsupportedDefault")
                                        .invoke(mSnippetManager, new JSONArray()));
        assertEquals(
                "No predefined converter found for class java.lang.Object", thrown.getMessage());
    }
}
