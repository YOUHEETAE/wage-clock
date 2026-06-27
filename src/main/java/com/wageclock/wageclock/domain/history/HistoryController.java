package com.wageclock.wageclock.domain.history;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/histories")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }
    @Operation(summary = "고용 이력 타임라인 조회 (근무/선지급 이벤트 시간순)")
    @GetMapping("/{employmentId}")
    public HistoryResponse getHistories(@PathVariable Long employmentId,
                                        @AuthenticationPrincipal Long callerId,
                                        @RequestParam(required = false) String after,
                                        @RequestParam(defaultValue = "20") int size){
        return historyService.getHistories(employmentId, callerId, after, size);
    }
}
