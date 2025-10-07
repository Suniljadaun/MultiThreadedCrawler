package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sunil.finintel.messaging.EventEnvelope;
import com.sunil.finintel.messaging.OutboxWriter;
import com.sunil.finintel.messaging.ProcessedEvents;
import com.sunil.finintel.messaging.Topics;
import com.sunil.finintel.portfolio.FillResult;
import com.sunil.finintel.portfolio.MarketPrice;
import com.sunil.finintel.portfolio.MarketPriceRepository;
import com.sunil.finintel.portfolio.PortfolioService;
import com.sunil.finintel.portfolio.Position;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private MarketPriceRepository marketPriceRepository;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private ProcessedEvents processedEvents;

    @Mock
    private OutboxWriter outboxWriter;

    @InjectMocks
    private OrderExecutionService service;

    private final UUID eventId = UUID.randomUUID();
    private final EventEnvelope event = new EventEnvelope(eventId, "OrderValidated", "1", 1L, Instant.now(), 1, null);

    private Order validatedOrder(OrderSide side, String limit) {
        Order order = new Order(1L, "ACME", side, 10, new BigDecimal(limit), "k", "h");
        order.moveTo(OrderStatus.VALIDATED);
        return order;
    }

    private void givenNewEventFor(Order order) {
        when(processedEvents.markProcessed(OrderExecutionService.CONSUMER_NAME, eventId)).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    }

    private void givenMarketPrice(String price) {
        when(marketPriceRepository.findById("ACME"))
                .thenReturn(Optional.of(new MarketPrice("ACME", new BigDecimal(price))));
    }

    @Test
    void buyWithinLimitIsExecuted() {
        Order order = validatedOrder(OrderSide.BUY, "101.50");
        givenNewEventFor(order);
        givenMarketPrice("100");
        Position position = new Position(1L, "ACME");
        position.buy(10, new BigDecimal("100"));
        when(portfolioService.buy(1L, "ACME", 10, new BigDecimal("100")))
                .thenReturn(new FillResult(true, 10, position));
        when(executionRepository.saveAndFlush(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleOrderValidated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        verify(outboxWriter).append(eq(Topics.ORDERS_EXECUTED), eq("OrderExecuted"), eq("1"), eq(1L), any());
        verify(outboxWriter).append(eq(Topics.PORTFOLIO_UPDATED), eq("PositionUpdated"), eq("1:ACME"), eq(1L), any());
    }

    @Test
    void buyAboveLimitIsRejected() {
        Order order = validatedOrder(OrderSide.BUY, "99");
        givenNewEventFor(order);
        givenMarketPrice("100");

        service.handleOrderValidated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verifyNoInteractions(portfolioService, executionRepository);
        verify(outboxWriter).append(eq(Topics.ORDERS_REJECTED), eq("OrderRejected"), anyString(), eq(1L), any());
    }

    @Test
    void sellWithoutEnoughSharesIsRejected() {
        Order order = validatedOrder(OrderSide.SELL, "90");
        givenNewEventFor(order);
        givenMarketPrice("100");
        when(portfolioService.sell(1L, "ACME", 10)).thenReturn(new FillResult(false, 3, null));

        service.handleOrderValidated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(executionRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEventChangesNothing() {
        when(processedEvents.markProcessed(OrderExecutionService.CONSUMER_NAME, eventId)).thenReturn(false);

        service.handleOrderValidated(event);

        verifyNoInteractions(orderRepository, portfolioService, executionRepository, outboxWriter);
    }

    @Test
    void cancelledOrderIsNotExecuted() {
        Order order = validatedOrder(OrderSide.BUY, "101.50");
        order.moveTo(OrderStatus.CANCELLED);
        givenNewEventFor(order);

        service.handleOrderValidated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(portfolioService, outboxWriter);
    }

    @Test
    void limitRulesPerSide() {
        assertThat(OrderExecutionService.withinLimit(validatedOrder(OrderSide.BUY, "100"), new BigDecimal("100"))).isTrue();
        assertThat(OrderExecutionService.withinLimit(validatedOrder(OrderSide.BUY, "100"), new BigDecimal("100.01"))).isFalse();
        assertThat(OrderExecutionService.withinLimit(validatedOrder(OrderSide.SELL, "100"), new BigDecimal("100"))).isTrue();
        assertThat(OrderExecutionService.withinLimit(validatedOrder(OrderSide.SELL, "100"), new BigDecimal("99.99"))).isFalse();
    }
}
