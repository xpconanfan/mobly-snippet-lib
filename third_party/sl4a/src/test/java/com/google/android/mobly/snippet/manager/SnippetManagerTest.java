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

package com.google.android.mobly.snippet.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for duplicate RPC method name detection in {@link SnippetManager}. */
@RunWith(JUnit4.class)
public class SnippetManagerTest {

    public static class FirstSnippet implements Snippet {
        @Rpc(description = "Unique RPC in FirstSnippet.")
        public void firstAction() {}

        @Rpc(description = "Conflicting RPC in FirstSnippet.")
        public void sharedAction() {}

        @Override
        public void shutdown() {}
    }

    public static class DistinctSnippet implements Snippet {
        @AsyncRpc(description = "Unique AsyncRpc in DistinctSnippet.")
        public void secondAction(String callbackId) {}

        @Override
        public void shutdown() {}
    }

    public static class ConflictingSecondSnippet implements Snippet {
        @Rpc(description = "Conflicting RPC in ConflictingSecondSnippet.")
        public String sharedAction(int count) {
            return String.valueOf(count);
        }

        @Override
        public void shutdown() {}
    }

    public static class OverloadedRpcSnippet implements Snippet {
        @Rpc(description = "First overload.")
        public void overloadedAction(int value) {}

        @Rpc(description = "Second overload.")
        public void overloadedAction(String label) {}

        @Override
        public void shutdown() {}
    }

    public static class SyncAndAsyncSameNameSnippet implements Snippet {
        @Rpc(description = "Sync version.")
        public void duplicateModeAction() {}

        @AsyncRpc(description = "Async version with same name.")
        public void duplicateModeAction(String callbackId) {}

        @Override
        public void shutdown() {}
    }

    public abstract static class BaseSnippet implements Snippet {
        @Rpc(description = "Base RPC.")
        public Number getValue() {
            return 1;
        }

        @Override
        public void shutdown() {}
    }

    public static class CovariantOverrideSnippet extends BaseSnippet {
        @Override
        @Rpc(description = "Covariant override RPC.")
        public Integer getValue() {
            return 2;
        }
    }

    public static class SecondSubclassSnippet extends BaseSnippet {}

    @Test
    public void constructor_distinctRpcsAcrossSnippets_registersAllMethods() {
        SnippetManager manager =
                new SnippetManager(Arrays.asList(FirstSnippet.class, DistinctSnippet.class));

        assertEquals(
                new HashSet<>(Arrays.asList("firstAction", "sharedAction", "secondAction")),
                manager.getMethodNames());
    }

    @Test
    public void constructor_duplicateRpcAcrossClasses_throwsActionableException() {
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SnippetManager(
                                        Arrays.asList(
                                                FirstSnippet.class,
                                                ConflictingSecondSnippet.class)));

        String message = thrown.getMessage();
        assertTrue(message.contains("Duplicate RPC name 'sharedAction'"));
        assertTrue(message.contains(FirstSnippet.class.getCanonicalName() + "#sharedAction()"));
        assertTrue(
                message.contains(
                        ConflictingSecondSnippet.class.getCanonicalName() + "#sharedAction(int)"));
    }

    @Test
    public void constructor_overloadedRpcsInSameClass_throwsActionableException() {
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SnippetManager(
                                        Collections.singletonList(OverloadedRpcSnippet.class)));

        String message = thrown.getMessage();
        assertTrue(message.contains("Duplicate RPC name 'overloadedAction'"));
        assertTrue(
                message.contains(
                        OverloadedRpcSnippet.class.getCanonicalName()
                                + "#overloadedAction(String)"));
        assertTrue(
                message.contains(
                        OverloadedRpcSnippet.class.getCanonicalName() + "#overloadedAction(int)"));
    }

    @Test
    public void constructor_syncAndAsyncSameNameInSameClass_throwsActionableException() {
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SnippetManager(
                                        Collections.singletonList(
                                                SyncAndAsyncSameNameSnippet.class)));

        String message = thrown.getMessage();
        assertTrue(message.contains("Duplicate RPC name 'duplicateModeAction'"));
        assertTrue(
                message.contains(
                        SyncAndAsyncSameNameSnippet.class.getCanonicalName()
                                + "#duplicateModeAction()"));
        assertTrue(
                message.contains(
                        SyncAndAsyncSameNameSnippet.class.getCanonicalName()
                                + "#duplicateModeAction(String)"));
    }

    @Test
    public void constructor_inheritedRpcAcrossTwoSubclasses_identifiesBothSubclasses() {
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SnippetManager(
                                        Arrays.asList(
                                                CovariantOverrideSnippet.class,
                                                SecondSubclassSnippet.class)));

        String message = thrown.getMessage();
        assertTrue(message.contains("Duplicate RPC name 'getValue'"));
        assertTrue(
                message.contains(
                        CovariantOverrideSnippet.class.getCanonicalName() + "#getValue()"));
        assertTrue(
                message.contains(SecondSubclassSnippet.class.getCanonicalName() + "#getValue()"));
    }

    @Test
    public void constructor_covariantReturnTypeOverride_ignoresSyntheticBridgeMethod() {
        SnippetManager manager =
                new SnippetManager(Collections.singletonList(CovariantOverrideSnippet.class));

        assertEquals(Collections.singleton("getValue"), manager.getMethodNames());
    }
}
