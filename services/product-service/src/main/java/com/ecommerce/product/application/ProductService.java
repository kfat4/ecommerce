package com.ecommerce.product.application;

import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.web.BusinessException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.product.api.ProductDtos.CreateProductRequest;
import com.ecommerce.product.api.ProductDtos.ProductExport;
import com.ecommerce.product.api.ProductDtos.ProductResponse;
import com.ecommerce.product.api.ProductDtos.UpdateProductRequest;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.ProductRepository;
import com.ecommerce.product.outbox.MongoOutboxPublisher;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Cache-aside with Redis: reads hit Redis first; writes update MongoDB and then refresh/evict the cache entry.
 * The cache is shared by all replicas, so an update on replica A is immediately visible on replica B.
 * <p>
 * Every write also stores a {@code ProductEvent} in the outbox <b>in the same MongoDB transaction</b>, so the
 * search index (and any future consumer) can never miss a change or see a change that was rolled back.
 */
@Service
public class ProductService {

    public static final String CACHE = "products";
    private static final String AGGREGATE = "Product";

    private final ProductRepository repository;
    private final MongoOutboxPublisher outbox;

    public ProductService(ProductRepository repository, MongoOutboxPublisher outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Cacheable(cacheNames = CACHE, key = "#id")
    public ProductResponse get(String id) {
        return ProductResponse.from(findLive(id));
    }

    public PageResponse<ProductResponse> list(String category, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name"));
        var result = category == null
                ? repository.findByActiveTrue(pageable)
                : repository.findByCategoryAndActiveTrue(category, pageable);
        return PageResponse.of(result, ProductResponse::from);
    }

    /** Batch lookup for order-service: one round trip instead of N calls (avoid chatty service-to-service APIs). */
    public List<ProductResponse> findAll(Collection<String> ids) {
        return repository.findAllById(ids).stream()
                .filter(p -> !p.isDeleted())
                .map(ProductResponse::from)
                .toList();
    }

    /** Full catalogue export (including soft-deleted products) used to (re)build read models such as the search index. */
    public PageResponse<ProductExport> export(int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 500), Sort.by("id"));
        return PageResponse.of(repository.findAll(pageable), ProductExport::from);
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        var product = new Product("PRD-" + request.sku().toUpperCase(Locale.ROOT), request.sku(),
                request.name(), request.description(), request.category(), request.price(), request.currency(),
                request.attributes());
        try {
            Product saved = repository.insert(product);
            outbox.publish(Topics.PRODUCT_EVENTS, saved.getId(), ProductEvents.upserted(saved));
            return ProductResponse.from(saved);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("sku-already-exists", "A product with this SKU already exists.");
        }
    }

    /** Used by the demo seeder. Throws {@link DuplicateKeyException} if another replica inserted it first. */
    @Transactional
    public void seed(Product product) {
        Product saved = repository.insert(product);
        outbox.publish(Topics.PRODUCT_EVENTS, saved.getId(), ProductEvents.upserted(saved));
    }

    @Transactional
    @CachePut(cacheNames = CACHE, key = "#id")
    public ProductResponse update(String id, UpdateProductRequest request) {
        Product product = findLive(id);
        product.update(request.name(), request.description(), request.price(), request.attributes(), request.active());
        // @Version -> optimistic locking: a concurrent update raises OptimisticLockingFailureException (HTTP 409).
        Product saved = repository.save(product);
        outbox.publish(Topics.PRODUCT_EVENTS, saved.getId(), ProductEvents.upserted(saved));
        return ProductResponse.from(saved);
    }

    /** Soft delete; idempotent – deleting an already deleted product is a no-op (DELETE is idempotent in HTTP). */
    @Transactional
    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void delete(String id) {
        Product product = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(AGGREGATE, id));
        if (product.isDeleted()) {
            return;
        }
        product.markDeleted();
        Product saved = repository.save(product);
        outbox.publish(Topics.PRODUCT_EVENTS, saved.getId(), ProductEvents.deleted(saved));
    }

    private Product findLive(String id) {
        return repository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(AGGREGATE, id));
    }
}
