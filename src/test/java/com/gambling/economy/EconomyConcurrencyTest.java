package com.gambling.economy;

import com.gambling.util.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyConcurrencyTest {
    private static EconomyManager createManager(Path dir) {
        EconomyManager manager = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        manager.loadFromDisk();
        return manager;
    }

    @Test
    void concurrentUpdatesOnSeparateProfilesLoseNoUpdates(@TempDir Path dir) throws Exception {
        EconomyManager manager = createManager(dir);
        int threads = 8;
        int operations = 400;
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            manager.getOrCreate(id, "Player" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                UUID id = ids.get(t);
                tasks.add(() -> {
                    for (int i = 0; i < operations; i++) {
                        if (i % 2 == 0) {
                            assertTrue(manager.addChips(id, "x", 1, "STRESS_ADD"));
                        } else {
                            assertTrue(manager.removeChips(id, "x", 1, "STRESS_REMOVE"));
                        }
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (UUID id : ids) {
            assertEquals(EconomyManager.DEFAULT_CHIPS, manager.getProfile(id).chips(),
                    "add/remove pairs must balance exactly under concurrency");
        }
        assertTrue(manager.isDirty());
        assertTrue(manager.save());
        assertTrue(!manager.isDirty());
    }

    @Test
    void concurrentBalanceInvariantNeverViolated(@TempDir Path dir) throws Exception {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                final long delta = (t % 2 == 0) ? 10 : -7;
                tasks.add(() -> {
                    for (int i = 0; i < 500; i++) {
                        if (delta > 0) {
                            manager.addChips(id, "Steve", delta, "STRESS");
                        } else {
                            manager.removeChips(id, "Steve", -delta, "STRESS");
                        }
                        long balance = manager.getProfile(id).chips();
                        assertTrue(balance >= 0 && balance <= Validation.MAX_ADMIN_AMOUNT,
                                "balance out of bounds: " + balance);
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Both adder threads always succeed (+10000 total: adds only fail at the
        // balance cap), so balance = 11000 - 7*(successful removals). Removals
        // are 7 each and only fail when the balance would drop below zero.
        // Therefore the final balance must stay in [0, 11000] and be congruent
        // to 11000 mod 7: a lost or duplicated add breaks the congruence.
        long balance = manager.getProfile(id).chips();
        assertTrue(balance >= 0 && balance <= Validation.MAX_ADMIN_AMOUNT);
        assertTrue(balance <= 11_000L, "balance exceeded the reachable maximum: " + balance);
        assertEquals(11_000L % 7, balance % 7,
                "lost or duplicated updates detected, final balance: " + balance);
    }
}
