package com.akto.test_editor.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class VariableResolverInterruptTest {

    @AfterEach
    public void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    public void alreadyInterruptedThreadStopsBeforeCartesianWork() {
        Thread.currentThread().interrupt();
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_payload", Collections.singletonList("a"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> VariableResolver.resolveWordListVar("${payload}", varMap));
        assertTrue(thrown.getMessage().contains("interrupted"));
        assertTrue(Thread.currentThread().isInterrupted(), "must not clear the interrupt flag");
    }

    @Test
    public void wordListLoopStopsWhenThreadIsInterrupted() throws Exception {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_payload", Arrays.asList("a-${payload}", "b-${payload}"));

        AtomicBoolean threw = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            try {
                VariableResolver.resolveWordListVar("${payload}", varMap);
            } catch (RuntimeException e) {
                threw.set(true);
            }
        });
        worker.start();
        worker.interrupt();
        worker.join(2000);

        assertFalse(worker.isAlive(), "word-list worker must exit on interrupt instead of spinning");
        assertTrue(threw.get());
    }
}
