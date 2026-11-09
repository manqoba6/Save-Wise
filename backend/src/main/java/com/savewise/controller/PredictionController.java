package com.savewise.controller;

import com.savewise.dto.AssistantDto.PredictionResponse;
import com.savewise.service.CurrentUserService;
import com.savewise.service.PredictionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {
    private final PredictionService service;
    private final CurrentUserService currentUser;

    public PredictionController(PredictionService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Balance and savings forecasts for 1, 3, 6 and 12 months ahead, plus goal forecasts. */
    @GetMapping
    public PredictionResponse predict(Authentication auth) {
        return service.predict(currentUser.get(auth));
    }
}
