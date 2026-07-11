package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.Authentication;
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
                                        Authentication authentication,
                                        @RequestParam(required = false) String after,
                                        @RequestParam(defaultValue = "20") int size){
        UserRole role = UserRole.valueOf(authentication.getAuthorities().iterator().next().getAuthority());
        return historyService.getHistories(employmentId, callerId, role, after, size);
    }
}
