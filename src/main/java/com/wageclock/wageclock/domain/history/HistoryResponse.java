package com.wageclock.wageclock.domain.history;


import java.util.List;

public record HistoryResponse(Long employmentId, List<HistoryEvent> events) {
}
