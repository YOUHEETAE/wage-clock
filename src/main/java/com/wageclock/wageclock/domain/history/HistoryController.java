package com.wageclock.wageclock.domain.history;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/histories")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }
    @GetMapping("/{employmentId}")
    public HistoryResponse getHistories(@PathVariable Long employmentId,
                                        @AuthenticationPrincipal Long callerId){
        return historyService.getHistories(employmentId, callerId);
    }
}
