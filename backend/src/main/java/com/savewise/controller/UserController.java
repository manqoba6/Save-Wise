package com.savewise.controller;

import com.savewise.dto.*;
import com.savewise.service.CurrentUserService;
import com.savewise.service.ProfileService;
import com.savewise.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final ProfileService profiles;
    private final UserAdminService admin;
    private final CurrentUserService currentUser;

    public UserController(ProfileService profiles, UserAdminService admin, CurrentUserService currentUser) {
        this.profiles = profiles;
        this.admin = admin;
        this.currentUser = currentUser;
    }

    // ---------- own profile ----------

    @GetMapping("/me")
    public ProfileResponse me(Authentication auth) {
        return profiles.get(currentUser.get(auth));
    }

    @PutMapping("/me")
    public ProfileResponse update(Authentication auth, @Valid @RequestBody UpdateProfileRequest req) {
        return profiles.update(currentUser.get(auth), req);
    }

    /** multipart/form-data with a "file" part (JPEG, PNG or WebP, max 2 MB). */
    @PostMapping(value = "/me/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadPicture(Authentication auth, @RequestParam("file") MultipartFile file) {
        return profiles.uploadPicture(currentUser.get(auth), file);
    }

    /** Returns the image bytes. Needs the Bearer token, so the frontend should fetch it and use a blob URL. */
    @GetMapping("/me/picture")
    public ResponseEntity<byte[]> picture(Authentication auth) {
        var pic = profiles.loadPicture(currentUser.get(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pic.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(pic.bytes());
    }

    @DeleteMapping("/me/picture")
    public ResponseEntity<Void> deletePicture(Authentication auth) {
        profiles.deletePicture(currentUser.get(auth));
        return ResponseEntity.noContent().build();
    }

    // ---------- administration (ADMIN only) ----------

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminUserResponse> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return admin.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse get(@PathVariable Long id) {
        return admin.get(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse setStatus(Authentication auth, @PathVariable Long id, @Valid @RequestBody UserStatusRequest req) {
        return admin.setEnabled(currentUser.get(auth), id, req.enabled());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        admin.delete(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }
}
