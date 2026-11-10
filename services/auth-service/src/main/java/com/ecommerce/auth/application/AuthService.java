package com.ecommerce.auth.application;

import com.ecommerce.auth.domain.UserAccount;
import com.ecommerce.auth.domain.UserAccountRepository;
import com.ecommerce.common.util.UuidV7;
import com.ecommerce.common.web.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Used to spend the same CPU time for unknown e-mails as for wrong passwords (prevents user enumeration). */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public UserAccount register(String email, String password, String fullName, List<String> roles) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("email-already-registered", "This e-mail address is already registered.");
        }
        try {
            return users.saveAndFlush(new UserAccount(UuidV7.next(), normalizedEmail, passwordEncoder.encode(password),
                    fullName, roles));
        } catch (DataIntegrityViolationException e) {
            // Two replicas raced on the same e-mail: the unique index is the real guard, the exists() check is UX.
            throw new BusinessException("email-already-registered", "This e-mail address is already registered.");
        }
    }

    @Transactional(readOnly = true)
    public TokenService.IssuedToken login(String email, String password) {
        var user = users.findByEmailIgnoreCase(email.trim());
        String hash = user.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid e-mail or password");
        }
        return tokenService.issue(user.get());
    }
}
