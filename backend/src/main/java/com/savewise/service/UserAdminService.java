package com.savewise.service;

import com.savewise.dto.AdminUserResponse;
import com.savewise.dto.PageResponse;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.RefreshTokenRepository;
import com.savewise.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;

    public UserAdminService(UserRepository users, RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id"));
        return PageResponse.from(users.findAll(pageable).map(UserAdminService::toResponse));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public AdminUserResponse setEnabled(User admin, Long id, boolean enabled) {
        if (admin.getId().equals(id) && !enabled) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account.");
        }
        User u = find(id);
        u.setEnabled(enabled);
        if (!enabled) refreshTokens.deleteByUserId(u.getId()); // cut off their sessions
        return toResponse(u);
    }

    @Transactional
    public void delete(User admin, Long id) {
        if (admin.getId().equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot delete your own account.");
        }
        users.delete(find(id)); // the database cascades to all of the user's data
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }

    static AdminUserResponse toResponse(User u) {
        return new AdminUserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getRole().getRoleName(), u.isEnabled(), u.getCreatedAt());
    }
}
