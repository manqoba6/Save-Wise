package com.savewise.service;

import com.savewise.dto.ProfileResponse;
import com.savewise.dto.UpdateProfileRequest;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.savewise.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class ProfileService {
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    public record Picture(byte[] bytes, String contentType) {}

    private final UserRepository users;
    private final Path root;

    public ProfileService(UserRepository users, @Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.users = users;
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(User current) {
        return toResponse(load(current));
    }

    @Transactional
    public ProfileResponse update(User current, UpdateProfileRequest req) {
        User u = load(current);
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setMonthlyIncome(req.monthlyIncome());
        return toResponse(u);
    }

    @Transactional
    public ProfileResponse uploadPicture(User current, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose an image to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image must be 2 MB or smaller.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the uploaded file.");
        }
        // Trust the file's actual bytes, not the filename or Content-Type the client sent.
        String ext = detectExtension(bytes);
        if (ext == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG or WebP images are allowed.");
        }
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.write(root.resolve(name), bytes);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the image.");
        }
        User u = load(current);
        String old = u.getProfilePicture();
        u.setProfilePicture(name);
        deleteQuietly(old);
        return toResponse(u);
    }

    @Transactional(readOnly = true)
    public Picture loadPicture(User current) {
        String name = load(current).getProfilePicture();
        if (name == null) throw new ApiException(HttpStatus.NOT_FOUND, "No profile picture set.");
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No profile picture set.");
        }
        try {
            return new Picture(Files.readAllBytes(file), contentTypeFor(name));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the image.");
        }
    }

    @Transactional
    public void deletePicture(User current) {
        User u = load(current);
        deleteQuietly(u.getProfilePicture());
        u.setProfilePicture(null);
    }

    // ---------- helpers ----------

    private User load(User current) {
        return users.findById(current.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists."));
    }

    private void deleteQuietly(String name) {
        if (name == null) return;
        try {
            Path file = root.resolve(name).normalize();
            if (file.startsWith(root)) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // an orphaned file is harmless
        }
    }

    private static String detectExtension(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    private static String contentTypeFor(String name) {
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    static ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                u.getMonthlyIncome(), u.getProfilePicture() != null, u.getRole().getRoleName(), u.getCreatedAt());
    }
}
