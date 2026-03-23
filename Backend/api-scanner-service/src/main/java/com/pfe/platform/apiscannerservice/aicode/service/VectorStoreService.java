package com.pfe.platform.apiscannerservice.aicode.service;

import com.pfe.platform.apiscannerservice.aicode.model.CodeChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VectorStoreService {

    public record StoredVector(CodeChunk chunk, float[] embedding) {}

    private final Map<String, StoredVector> store = new ConcurrentHashMap<>();

    public void put(CodeChunk chunk, float[] embedding) {
        if (chunk == null || embedding == null) throw new IllegalArgumentException("chunk and embedding required");
        store.put(chunk.id(), new StoredVector(chunk, embedding));
    }

    public List<StoredVector> topKSimilar(float[] queryEmbedding, int k) {
        if (queryEmbedding == null) throw new IllegalArgumentException("queryEmbedding required");
        if (k <= 0) return List.of();

        List<Scored> scored = new ArrayList<>(store.size());
        for (StoredVector v : store.values()) {
            double s = cosineSimilarity(queryEmbedding, v.embedding());
            scored.add(new Scored(v, s));
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int limit = Math.min(k, scored.size());
        List<StoredVector> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(scored.get(i).vector());
        }
        return out;
    }

    private record Scored(StoredVector vector, double score) {}

    public static double cosineSimilarity(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        if (len == 0) return 0.0;

        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
