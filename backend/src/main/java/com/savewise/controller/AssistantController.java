package com.savewise.controller;

import com.savewise.dto.AssistantDto.*;
import com.savewise.service.AssistantService;
import com.savewise.service.CurrentUserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistant;
    private final CurrentUserService currentUser;

    public AssistantController(AssistantService assistant, CurrentUserService currentUser) {
        this.assistant = assistant;
        this.currentUser = currentUser;
    }

    /** Everything worth knowing, most urgent first, plus the financial health score. ?month=2026-10-01 optional. */
    @GetMapping("/advice")
    public AdviceResponse advice(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return assistant.advice(currentUser.get(auth), month);
    }

    /** Budget breaches, categories running hot and spending above income for one month (default: this month). */
    @GetMapping("/overspending")
    public OverspendingResponse overspending(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return assistant.overspending(currentUser.get(auth), month);
    }

    @GetMapping("/saving-recommendations")
    public RecommendationsResponse savingRecommendations(Authentication auth) {
        return assistant.savingRecommendations(currentUser.get(auth));
    }

    /** Income, spending and category trends over the last ?months= full months (2 to 12, default 6). */
    @GetMapping("/spending-trends")
    public TrendsResponse spendingTrends(Authentication auth, @RequestParam(defaultValue = "6") int months) {
        return assistant.trends(currentUser.get(auth), months);
    }
}
