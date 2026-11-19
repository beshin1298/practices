package com.warden.user.service.impl;

import static com.warden.security.service.AttemptLimiter.Bucket.PASSWORD_CHECK;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.common.validation.PasswordPolicyValidator;
import com.warden.security.service.AttemptLimiter;
import com.warden.user.dto.request.ChangePasswordRequest;
import com.warden.user.dto.request.UpdateOwnProfileRequest;
import com.warden.user.dto.response.CurrentUserResponse;
import com.warden.user.entity.User;
import com.warden.user.event.UserSecurityChangedEvent;
import com.warden.user.mapper.UserMapper;
import com.warden.user.repository.UserRepository;
import com.warden.user.service.AccountService;

/** Default {@link AccountService}. */
@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final UserRepository users;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AttemptLimiter limiter;
    private final TransactionTemplate transactions;

    public AccountServiceImpl(UserRepository users, UserMapper userMapper, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events, AttemptLimiter limiter, TransactionTemplate transactions) {
        this.users = users;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.limiter = limiter;
        this.transactions = transactions;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getProfile(Long userId) {
        return userMapper.toCurrentUser(load(userId));
    }

    @Override
    public CurrentUserResponse updateProfile(Long userId, UpdateOwnProfileRequest request) {
        User user = load(userId);
        user.setFullName(request.fullName().trim());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        return userMapper.toCurrentUser(user);
    }

    /**
     * BCrypt (verify + hash, ~200 ms) runs outside any transaction so no pooled connection is held
     * meanwhile; only the final write is transactional. Wrong current passwords are rate limited.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void changePassword(Long userId, ChangePasswordRequest request) {
        limiter.checkAllowed(PASSWORD_CHECK, userId);
        User user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String verifiedHash = user.getPasswordHash();
        if (!PasswordPolicyValidator.fitsBcrypt(request.currentPassword())
                || !passwordEncoder.matches(request.currentPassword(), verifiedHash)) {
            limiter.recordFailure(PASSWORD_CHECK, userId);
            audit.warn("action=password_change_wrong_current userId={}", userId);
            throw new AppException(ErrorCode.WRONG_CURRENT_PASSWORD);
        }
        limiter.reset(PASSWORD_CHECK, userId);
        // The current password was just verified, so a plain comparison replaces a second BCrypt.
        if (request.newPassword().equals(request.currentPassword())) {
            throw new AppException(ErrorCode.PASSWORD_REUSED);
        }
        String newHash = passwordEncoder.encode(request.newPassword());

        transactions.executeWithoutResult(status -> {
            User fresh = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            if (!fresh.getPasswordHash().equals(verifiedHash)) { // changed by a parallel request meanwhile
                throw new AppException(ErrorCode.WRONG_CURRENT_PASSWORD);
            }
            fresh.changePassword(newHash, Instant.now());
            // Same transaction: the auth module revokes every refresh token before this commits.
            events.publishEvent(new UserSecurityChangedEvent(userId, UserSecurityChangedEvent.Reason.PASSWORD_CHANGED));
        });
        audit.info("action=password_changed userId={}", userId);
    }

    private User load(Long userId) {
        return users.findForAuthenticationById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
