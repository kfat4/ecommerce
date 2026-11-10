package com.ecommerce.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

/** Graceful degradation: a fast, explicit 503 instead of a hanging connection or a raw 500. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    Mono<ResponseEntity<ProblemDetail>> fallback(@PathVariable String service) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                service + " is temporarily unavailable. Please retry shortly.");
        problem.setType(URI.create("https://errors.ecommerce.example/service-unavailable"));
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(problem));
    }
}
