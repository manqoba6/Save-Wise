package com.savewise.service;

import com.savewise.dto.GoalContributionRequest;
import com.savewise.dto.GoalRequest;
import com.savewise.dto.GoalResponse;
import com.savewise.entity.FinancialGoal;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.FinancialGoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.savewise.util.CurrencyFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/** Flow from the Backend Guide: Create Goal -> Save Money (contribute) -> Update Progress -> Goal Completed -> Notification. */
@Service
public class GoalService {
    private final FinancialGoalRepository repo;
    private final NotificationService notifications;
    private final PreferenceService preferences;

    public GoalService(FinancialGoalRepository repo, NotificationService notifications, PreferenceService preferences) {
        this.repo = repo;
        this.notifications = notifications;
        this.preferences = preferences;
    }

    @Transactional
    public GoalResponse create(User user, GoalRequest req) {
        FinancialGoal g = repo.save(FinancialGoal.builder()
                .user(user).title(req.title().trim()).targetAmount(req.targetAmount()).deadline(req.deadline()).build());
        return toResponse(g);
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(User user, String status) {
        List<FinancialGoal> found = (status == null || status.isBlank())
                ? repo.findByUserIdOrderByIdDesc(user.getId())
                : repo.findByUserIdAndStatusOrderByIdDesc(user.getId(), status.toUpperCase());
        return found.stream().map(GoalService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(User user, Long id) {
        return toResponse(owned(user, id));
    }

    @Transactional
    public GoalResponse update(User user, Long id, GoalRequest req) {
        FinancialGoal g = owned(user, id);
        if ("CANCELLED".equals(g.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "A cancelled goal cannot be edited.");
        }
        g.setTitle(req.title().trim());
        g.setTargetAmount(req.targetAmount());
        g.setDeadline(req.deadline());
        // The target may have been raised or lowered, so re-evaluate.
        if ("COMPLETED".equals(g.getStatus()) && g.getCurrentAmount().compareTo(g.getTargetAmount()) < 0) {
            g.setStatus("ACTIVE");
        }
        completeIfReached(user, g);
        return toResponse(g);
    }

    @Transactional
    public GoalResponse contribute(User user, Long id, GoalContributionRequest req) {
        FinancialGoal g = owned(user, id);
        if (!"ACTIVE".equals(g.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only active goals can receive contributions.");
        }
        g.setCurrentAmount(g.getCurrentAmount().add(req.amount()));
        completeIfReached(user, g);
        return toResponse(g);
    }

    @Transactional
    public GoalResponse cancel(User user, Long id) {
        FinancialGoal g = owned(user, id);
        if ("COMPLETED".equals(g.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "A completed goal cannot be cancelled.");
        }
        g.setStatus("CANCELLED");
        return toResponse(g);
    }

    @Transactional
    public void delete(User user, Long id) {
        repo.delete(owned(user, id));
    }

    private void completeIfReached(User user, FinancialGoal g) {
        if ("ACTIVE".equals(g.getStatus()) && g.getCurrentAmount().compareTo(g.getTargetAmount()) >= 0) {
            g.setStatus("COMPLETED");
            notifications.create(user, "GOAL_ACHIEVED", "Goal achieved",
                    "Congratulations! You reached your goal \"" + g.getTitle() + "\" ("
                            + CurrencyFormatter.format(g.getTargetAmount(), preferences.currencyFor(user)) + ").");
        }
    }

    private FinancialGoal owned(User user, Long id) {
        return repo.findById(id)
                .filter(g -> g.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found."));
    }

    static GoalResponse toResponse(FinancialGoal g) {
        BigDecimal pct = g.getCurrentAmount().multiply(BigDecimal.valueOf(100))
                .divide(g.getTargetAmount(), 1, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        return new GoalResponse(g.getId(), g.getTitle(), g.getTargetAmount(), g.getCurrentAmount(),
                g.getDeadline(), g.getStatus(), pct);
    }
}
