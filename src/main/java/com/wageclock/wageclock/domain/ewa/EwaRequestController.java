package com.wageclock.wageclock.domain.ewa;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ewaRequest")
public class EwaRequestController {

    private final EwaRequestService ewaRequestService;
    public EwaRequestController(EwaRequestService ewaRequestService) {
        this.ewaRequestService = ewaRequestService;
    }

    @PostMapping("/request")
    public EwaResponseDto request(@RequestBody EwaRequestDto requestDto){
        return ewaRequestService.requestEwa(requestDto);
    }
}
