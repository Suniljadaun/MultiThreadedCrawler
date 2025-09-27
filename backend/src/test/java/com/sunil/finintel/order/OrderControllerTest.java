package com.sunil.finintel.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sunil.finintel.common.ConflictException;
import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.common.PageResponse;
import com.sunil.finintel.common.UnprocessableException;

// Web layer only: real controller + exception handler, service is mocked
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String BODY =
            "{\"userId\":1,\"symbol\":\"ACME\",\"side\":\"BUY\",\"quantity\":10,\"price\":101.50}";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse sample() {
        return new OrderResponse(7L, 1L, "ACME", OrderSide.BUY, 10, new BigDecimal("101.5000"),
                OrderStatus.CREATED, NOW, NOW);
    }

    @Test
    void newOrderReturns201() throws Exception {
        when(orderService.place(eq("key-1"), any())).thenReturn(new PlaceOrderResult(sample(), true));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/7"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void replayReturns200() throws Exception {
        when(orderService.place(eq("key-1"), any())).thenReturn(new PlaceOrderResult(sample(), false));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
    }

    @Test
    void zeroQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"quantity\":10", "\"quantity\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownSideReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("BUY", "HOLD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void keyReusedWithDifferentBodyReturns422() throws Exception {
        when(orderService.place(eq("key-1"), any()))
                .thenThrow(new UnprocessableException("Idempotency-Key was already used with a different request"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE"));
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        when(orderService.get(99L)).thenThrow(new NotFoundException("order 99 not found"));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void listReturnsPage() throws Exception {
        when(orderService.listForUser(1L, 0, 20))
                .thenReturn(new PageResponse<>(List.of(sample()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancelReturns200() throws Exception {
        OrderResponse cancelled = new OrderResponse(7L, 1L, "ACME", OrderSide.BUY, 10, new BigDecimal("101.5000"),
                OrderStatus.CANCELLED, NOW, NOW);
        when(orderService.cancel(7L)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/orders/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelExecutedOrderReturns409() throws Exception {
        when(orderService.cancel(7L)).thenThrow(new ConflictException("order 7 is EXECUTED and cannot be cancelled"));

        mockMvc.perform(post("/api/v1/orders/7/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }
}
