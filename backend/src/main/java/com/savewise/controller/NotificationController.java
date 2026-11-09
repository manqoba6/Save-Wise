package com.savewise.controller;

import com.savewise.dto.NotificationResponse;
import com.savewise.dto.PageResponse;
import com.savewise.service.CurrentUserService;
import com.savewise.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final CurrentUserService currentUser;

    public NotificationController(NotificationService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(Authentication auth,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(currentUser.get(auth), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("unread", service.unreadCount(currentUser.get(auth)));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(Authentication auth, @PathVariable Long id) {
        return service.markRead(currentUser.get(auth), id);
    }

    @PutMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication auth) {
        return Map.of("updated", service.markAllRead(currentUser.get(auth)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }
}
