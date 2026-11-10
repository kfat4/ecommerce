package com.ecommerce.auth.application;

import com.ecommerce.auth.domain.UserAccountRepository;
import com.ecommerce.common.web.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Seeds demo accounts. Safe when several replicas start at the same time (unique e-mail index). */
@Component
@ConditionalOnProperty(name = "ecommerce.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoUsersInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUsersInitializer.class);

    private final AuthService authService;
    private final UserAccountRepository users;

    public DemoUsersInitializer(AuthService authService, UserAccountRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        create("customer@ecommerce.local", "Customer123!", "Demo Customer", List.of("CUSTOMER"));
        create("admin@ecommerce.local", "Admin123!", "Demo Admin", List.of("CUSTOMER", "ADMIN"));
    }

    private void create(String email, String password, String name, List<String> roles) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        try {
            authService.register(email, password, name, roles);
            log.info("Demo user created: {}", email);
        } catch (BusinessException alreadyThere) {
            log.debug("Demo user {} created concurrently by another replica", email);
        }
    }
}
