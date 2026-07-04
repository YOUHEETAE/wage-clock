package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.global.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employments")
public class EmploymentController {
    private final EmploymentService employmentService;

    public EmploymentController(EmploymentService employmentService) {
        this.employmentService = employmentService;
    }

    @Operation(summary = "고용 관계 등록 (고용주가 근로자 채용)")
    @PostMapping
    public EmploymentResponse createEmployment(@RequestBody EmploymentRequest employmentRequest,
                                               @AuthenticationPrincipal Long employerId,
                                               Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("EMPLOYER"))) {
            throw new UnauthorizedException("Unauthorized");
        }
        return employmentService.createEmployment(employmentRequest, employerId);
    }
}
