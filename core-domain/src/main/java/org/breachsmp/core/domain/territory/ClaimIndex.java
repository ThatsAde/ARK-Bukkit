package org.breachsmp.core.domain.territory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Indice spaziale dei claim (spec §6, portato da ToolCupboards {@code ClaimIndex}): ogni claim è nei bucket dei chunk che
 * tocca, un lookup legge un solo bucket e fa pochi test esatti. Non thread-safe: si usa solo dal main thread.
 *
 * @param <T> il claim (es. {@code LiveClaim})
 */
public final class ClaimIndex<T> {

    private record ChunkKey(UUID world, int x, int z) {
    }

    private record Entry<T>(T claim, ClaimBox box) {
    }

    private final Map<ChunkKey, List<Entry<T>>> byChunk = new HashMap<>();
    private final Map<T, ClaimBox> boxes = new HashMap<>();

    public void add(T claim, ClaimBox box) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(box, "box");
        if (boxes.putIfAbsent(claim, box) != null) {
            throw new IllegalStateException("Claim già nell'indice: " + claim);
        }
        Entry<T> entry = new Entry<>(claim, box);
        forEachChunk(box, 0, key -> byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry));
    }

    public void remove(T claim) {
        ClaimBox box = boxes.remove(claim);
        if (box == null) {
            return;
        }
        forEachChunk(box, 0, key -> {
            List<Entry<T>> bucket = byChunk.get(key);
            if (bucket != null) {
                bucket.removeIf(entry -> entry.claim().equals(claim));
                if (bucket.isEmpty()) {
                    byChunk.remove(key);
                }
            }
        });
    }

    /** Claim che contengono il blocco, dal più vicino al cupboard. */
    public List<T> covering(UUID world, int x, int y, int z) {
        List<Entry<T>> bucket = byChunk.get(new ChunkKey(world, x >> 4, z >> 4));
        if (bucket == null) {
            return List.of();
        }
        List<Entry<T>> inside = new ArrayList<>(2);
        for (Entry<T> entry : bucket) {
            if (entry.box().contains(world, x, y, z)) {
                inside.add(entry);
            }
        }
        if (inside.size() > 1) {
            inside.sort(Comparator.comparingLong(entry -> entry.box().squaredDistance(x, y, z)));
        }
        return inside.stream().map(Entry::claim).toList();
    }

    /** Claim che si toccano con {@code box} allargato di {@code gap} blocchi. */
    public List<T> overlapping(ClaimBox box, int gap) {
        Set<T> found = new LinkedHashSet<>();
        forEachChunk(box, gap, key -> {
            List<Entry<T>> bucket = byChunk.get(key);
            if (bucket != null) {
                for (Entry<T> entry : bucket) {
                    if (entry.box().intersects(box, gap)) {
                        found.add(entry.claim());
                    }
                }
            }
        });
        return List.copyOf(found);
    }

    public int size() {
        return boxes.size();
    }

    private static void forEachChunk(ClaimBox box, int extra, java.util.function.Consumer<ChunkKey> action) {
        int reach = box.radius() + extra;
        for (int cx = (box.x() - reach) >> 4; cx <= (box.x() + reach) >> 4; cx++) {
            for (int cz = (box.z() - reach) >> 4; cz <= (box.z() + reach) >> 4; cz++) {
                action.accept(new ChunkKey(box.world(), cx, cz));
            }
        }
    }
}
