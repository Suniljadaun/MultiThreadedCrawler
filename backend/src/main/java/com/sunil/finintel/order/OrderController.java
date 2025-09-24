package com.sunil.finintel.order;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunil.finintel.common.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 201 for a new order, 200 when the same Idempotency-Key + body is replayed
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> place(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = orderService.place(idempotencyKey, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/orders/" + result.order().id())).body(result.order());
        }
        return ResponseEntity.ok(result.order());
    }

    @GetMapping("/orders/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @GetMapping("/users/{userId}/orders")
    public PageResponse<OrderResponse> listForUser(@PathVariable Long userId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return orderService.listForUser(userId, page, size);
    }
}
