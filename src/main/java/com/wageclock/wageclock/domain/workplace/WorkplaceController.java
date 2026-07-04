package com.wageclock.wageclock.domain.workplace;

import com.wageclock.wageclock.global.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workplaces")
public class WorkplaceController {

    private final WorkplaceService workplaceService;

    public WorkplaceController(WorkplaceService workplaceService) {
        this.workplaceService = workplaceService;
    }

    @Operation(summary = "사업장 등록")
    @PostMapping
    public WorkplaceResponse createWorkplace(@RequestBody WorkplaceRequest request,
                                             @AuthenticationPrincipal Long employerId,
                                             Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("EMPLOYER"))) {
            throw new UnauthorizedException("Unauthorized");
        }
        return workplaceService.createWorkplace(request, employerId);
    }

    @Operation(summary = "내 사업장 목록 조회 (고용주)")
    @GetMapping
    public List<WorkplaceResponse> getWorkplaces(@AuthenticationPrincipal Long employerId,
                                                 Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("EMPLOYER"))) {
            throw new UnauthorizedException("Unauthorized");
        }
        return workplaceService.getWorkplaces(employerId);
    }

    @Operation(summary = "내 사업장 목록 조회 (워커) - employmentId 포함")
    @GetMapping("/worker")
    public List<WorkerWorkplaceResponse> getWorkerWorkplaces(@AuthenticationPrincipal Long workerId,
                                                             Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("WORKER"))) {
            throw new UnauthorizedException("Unauthorized");
        }
        return workplaceService.getWorkerWorkplaces(workerId);
    }
}
