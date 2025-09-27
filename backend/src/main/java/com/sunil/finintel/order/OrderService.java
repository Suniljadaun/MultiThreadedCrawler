package com.sunil.finintel.order;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.common.BadRequestException;
import com.sunil.finintel.common.ConflictException;
import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.common.PageResponse;
import com.sunil.finintel.common.RequestHasher;
import com.sunil.finintel.common.UnprocessableException;
import com.sunil.finintel.user.UserRepository;

@Service
public class OrderService {

    static final int MAX_KEY_LENGTH = 100;
    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    // Idempotent order creation.
    // Deliberately NOT @Transactional: each repository call runs in its own transaction,
    // so after a failed INSERT (unique violation) we can still read the winning row.
    public PlaceOrderResult place(String idempotencyKey, PlaceOrderRequest request) {
        String key = validateKey(idempotencyKey);
        if (!userRepository.existsById(request.userId())) {
            throw new NotFoundException("user " + request.userId() + " not found");
        }
        String hash = requestHash(request);

        // 1. Key already used: replay the stored result
        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(request.userId(), key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        // 2. New key: insert. The unique constraint (user_id, idempotency_key) decides any race.
        Order order = new Order(request.userId(), normalizeSymbol(request.symbol()), request.side(),
                request.quantity(), request.price(), key, hash);
        try {
            return new PlaceOrderResult(OrderResponse.from(orderRepository.saveAndFlush(order)), true);
        } catch (DataIntegrityViolationException e) {
            // 3. Lost the race: a concurrent request with the same key committed first
            Order winner = orderRepository.findByUserIdAndIdempotencyKey(request.userId(), key)
                    .orElseThrow(() -> e);
            return replay(winner, hash);
        }
    }

    // Cancelling twice is harmless: an already-cancelled order is returned as-is.
    // A concurrent status change is caught by @Version and surfaces as HTTP 409.
    @Transactional
    public OrderResponse cancel(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("order " + id + " not found"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }
        if (!order.getStatus().canMoveTo(OrderStatus.CANCELLED)) {
            throw new ConflictException("order " + id + " is " + order.getStatus() + " and cannot be cancelled");
        }
        order.moveTo(OrderStatus.CANCELLED);
        // Flush now so updated_at in the response is the stored value
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("order " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listForUser(Long userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("user " + userId + " not found");
        }
        // Out-of-range values are clamped instead of rejected
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<OrderResponse> result = orderRepository.findByUserId(userId, pageable).map(OrderResponse::from);
        return PageResponse.from(result);
    }

    // Same key + same request -> same result. Same key + different request -> 422.
    private PlaceOrderResult replay(Order existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new UnprocessableException("Idempotency-Key was already used with a different request");
        }
        return new PlaceOrderResult(OrderResponse.from(existing), false);
    }

    private static String validateKey(String key) {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    // Fingerprint of the business content, so JSON formatting, "acme" vs "ACME"
    // or 101.5 vs 101.50 do not count as a different request.
    static String requestHash(PlaceOrderRequest r) {
        String canonical = r.userId() + "|" + normalizeSymbol(r.symbol()) + "|" + r.side() + "|"
                + r.quantity() + "|" + r.price().stripTrailingZeros().toPlainString();
        return RequestHasher.sha256Hex(canonical);
    }

    static String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
