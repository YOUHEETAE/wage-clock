package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.global.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
                                               @AuthenticationPrincipal Long employerId) {
        return employmentService.createEmployment(employmentRequest, employerId);
    }

    @Operation(summary = "내 고용 목록 조회 (근로자)")
    @GetMapping("/my")
    public List<EmploymentResponse> getMyEmployments(@AuthenticationPrincipal Long workerId,
                                                     Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("WORKER"))) {
            throw new UnauthorizedException("Unauthorized");
        }
        return employmentService.getMyEmployments(workerId);
    }

    @Operation(summary = "내 고용 목록 조회 (고용주)")
    @GetMapping("/employer")
    public List<EmploymentResponse> getEmployerEmployments(@AuthenticationPrincipal Long employerId,
                                                           Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("EMPLOYER"))) {
            throw new UnauthorizedException("Unauthorized");
        }
        return employmentService.getEmployerEmployments(employerId);
    }
}
