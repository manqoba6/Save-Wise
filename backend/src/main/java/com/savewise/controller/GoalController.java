package com.savewise.controller;

import com.savewise.dto.*;
import com.savewise.service.CurrentUserService;
import com.savewise.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {
    private final GoalService service;
    private final CurrentUserService currentUser;

    public GoalController(GoalService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(Authentication auth, @Valid @RequestBody GoalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(currentUser.get(auth), req));
    }

    /** Optional ?status=ACTIVE|COMPLETED|CANCELLED */
    @GetMapping
    public List<GoalResponse> list(Authentication auth, @RequestParam(required = false) String status) {
        return service.list(currentUser.get(auth), status);
    }

    @GetMapping("/{id}")
    public GoalResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(currentUser.get(auth), id);
    }

    @PutMapping("/{id}")
    public GoalResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody GoalRequest req) {
        return service.update(currentUser.get(auth), id, req);
    }

    /** "Save money" toward a goal. Completes the goal and sends a notification when the target is reached. */
    @PostMapping("/{id}/contribute")
    public GoalResponse contribute(Authentication auth, @PathVariable Long id, @Valid @RequestBody GoalContributionRequest req) {
        return service.contribute(currentUser.get(auth), id, req);
    }

    @PutMapping("/{id}/cancel")
    public GoalResponse cancel(Authentication auth, @PathVariable Long id) {
        return service.cancel(currentUser.get(auth), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }
}
