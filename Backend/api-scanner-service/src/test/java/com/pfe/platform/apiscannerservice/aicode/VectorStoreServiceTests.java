package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.model.CodeChunk;
import com.pfe.platform.apiscannerservice.aicode.service.VectorStoreService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreServiceTests {

    @Test
    void topKReturnsMostSimilarFirst() {
        VectorStoreService store = new VectorStoreService();

        store.put(new CodeChunk("1", "a", 0, "x"), new float[]{1, 0});
        store.put(new CodeChunk("2", "b", 0, "y"), new float[]{0, 1});

        List<VectorStoreService.StoredVector> top = store.topKSimilar(new float[]{0.9f, 0.1f}, 1);
        assertEquals(1, top.size());
        assertEquals("1", top.get(0).chunk().id());
    }

    @Test
    void cosineSimilarityHandlesZeroVector() {
        assertEquals(0.0, VectorStoreService.cosineSimilarity(new float[]{0, 0}, new float[]{1, 2}), 1e-9);
    }
}

