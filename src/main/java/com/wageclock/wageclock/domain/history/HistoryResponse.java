package com.wageclock.wageclock.domain.history;


import java.util.List;

public record HistoryResponse(Long employmentId, String nextCursor, boolean hasNext , List<HistoryEvent> events) {
}
