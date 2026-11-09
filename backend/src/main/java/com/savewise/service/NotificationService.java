package com.savewise.service;

import com.savewise.dto.NotificationResponse;
import com.savewise.dto.PageResponse;
import com.savewise.entity.Notification;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final PreferenceService preferences;

    public NotificationService(NotificationRepository repo, PreferenceService preferences) {
        this.repo = repo;
        this.preferences = preferences;
    }

    /** Joins the caller's transaction, so an alert is saved together with the expense that caused it. */
    @Transactional
    public void create(User user, String type, String title, String message) {
        if (!preferences.notificationsEnabled(user)) return; // user turned notifications off in Settings
        repo.save(Notification.builder().user(user).type(type).title(title).message(message).build());
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(User user, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Notification> found = unreadOnly
                ? repo.findByUserIdAndReadFalse(user.getId(), pageable)
                : repo.findByUserId(user.getId(), pageable);
        return PageResponse.from(found.map(NotificationService::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return repo.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public NotificationResponse markRead(User user, Long id) {
        Notification n = owned(user, id);
        n.setRead(true);
        return toResponse(n);
    }

    @Transactional
    public int markAllRead(User user) {
        return repo.markAllRead(user.getId());
    }

    @Transactional
    public void delete(User user, Long id) {
        repo.delete(owned(user, id));
    }

    private Notification owned(User user, Long id) {
        return repo.findById(id)
                .filter(n -> n.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found."));
    }

    static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.isRead(), n.getCreatedAt());
    }
}
