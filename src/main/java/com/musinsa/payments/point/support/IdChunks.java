package com.musinsa.payments.point.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class IdChunks {

    private static final int MAX_IDS_PER_QUERY = 1000;

    public static List<List<Long>> split(Collection<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<List<Long>> chunks = new ArrayList<>();
        for (int from = 0; from < distinct.size(); from += MAX_IDS_PER_QUERY) {
            chunks.add(distinct.subList(from, Math.min(from + MAX_IDS_PER_QUERY, distinct.size())));
        }
        return chunks;
    }

    private IdChunks() {
    }
}
