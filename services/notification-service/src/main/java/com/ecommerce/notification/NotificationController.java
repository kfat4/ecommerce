package com.ecommerce.notification;

import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    PageResponse<Notification> mine(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(repository.findByCustomerIdOrderByCreatedAtDesc(CurrentUser.id(),
                PageRequest.of(page, Math.min(size, 100))), n -> n);
    }
}
