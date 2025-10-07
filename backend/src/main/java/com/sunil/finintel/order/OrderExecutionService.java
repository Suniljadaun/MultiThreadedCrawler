package com.sunil.finintel.order;

import java.math.BigDecimal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.messaging.EventEnvelope;
import com.sunil.finintel.messaging.OutboxWriter;
import com.sunil.finintel.messaging.ProcessedEvents;
import com.sunil.finintel.messaging.Topics;
import com.sunil.finintel.portfolio.FillResult;
import com.sunil.finintel.portfolio.MarketPrice;
import com.sunil.finintel.portfolio.MarketPriceRepository;
import com.sunil.finintel.portfolio.PortfolioService;
import com.sunil.finintel.portfolio.PositionUpdatedPayload;

// Handles OrderValidated: VALIDATED -> EXECUTED or REJECTED (see ADR-006).
// One transaction covers: dedupe row, order status, execution row, position change and outgoing events.
@Service
public class OrderExecutionService {

    static final String CONSUMER_NAME = "order-execution";

    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final PortfolioService portfolioService;
    private final ProcessedEvents processedEvents;
    private final OutboxWriter outboxWriter;

    public OrderExecutionService(OrderRepository orderRepository, ExecutionRepository executionRepository,
                                 MarketPriceRepository marketPriceRepository, PortfolioService portfolioService,
                                 ProcessedEvents processedEvents, OutboxWriter outboxWriter) {
        this.orderRepository = orderRepository;
        this.executionRepository = executionRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.portfolioService = portfolioService;
        this.processedEvents = processedEvents;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void handleOrderValidated(EventEnvelope event) {
        if (!processedEvents.markProcessed(CONSUMER_NAME, event.eventId())) {
            log.info("Skipping duplicate event {}", event.eventId());
            return;
        }
        Long orderId = Long.valueOf(event.aggregateId());
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.VALIDATED) {
            // Missing, or e.g. cancelled after validation: nothing to execute
            log.info("Order {} not executable ({})", orderId, order == null ? "missing" : order.getStatus());
            return;
        }

        Optional<BigDecimal> marketPrice = marketPriceRepository.findById(order.getSymbol()).map(MarketPrice::getPrice);
        if (marketPrice.isEmpty()) {
            reject(order, "no market price for " + order.getSymbol());
            return;
        }
        BigDecimal price = marketPrice.get();
        if (!withinLimit(order, price)) {
            reject(order, "limit " + order.getRequestedPrice() + " not reached, market " + price);
            return;
        }

        FillResult fill = order.getSide() == OrderSide.BUY
                ? portfolioService.buy(order.getUserId(), order.getSymbol(), order.getQuantity(), price)
                : portfolioService.sell(order.getUserId(), order.getSymbol(), order.getQuantity());
        if (!fill.applied()) {
            reject(order, "insufficient position: holding " + fill.heldQuantity() + " " + order.getSymbol());
            return;
        }

        order.moveTo(OrderStatus.EXECUTED);
        Execution execution = executionRepository.saveAndFlush(new Execution(order, price));
        outboxWriter.append(Topics.ORDERS_EXECUTED, "OrderExecuted", String.valueOf(orderId),
                order.getUserId(), OrderExecutedPayload.from(execution));
        outboxWriter.append(Topics.PORTFOLIO_UPDATED, "PositionUpdated",
                order.getUserId() + ":" + order.getSymbol(), order.getUserId(),
                PositionUpdatedPayload.from(fill.position()));
        log.info("Order {} executed: {} {} {} at {}", orderId, order.getSide(), order.getQuantity(),
                order.getSymbol(), price);
    }

    // requestedPrice is a limit: BUY at most that price, SELL at least that price
    static boolean withinLimit(Order order, BigDecimal marketPrice) {
        int cmp = marketPrice.compareTo(order.getRequestedPrice());
        return order.getSide() == OrderSide.BUY ? cmp <= 0 : cmp >= 0;
    }

    private void reject(Order order, String reason) {
        order.moveTo(OrderStatus.REJECTED);
        outboxWriter.append(Topics.ORDERS_REJECTED, "OrderRejected", String.valueOf(order.getId()),
                order.getUserId(), new OrderStatusChangedPayload(order.getId(), OrderStatus.REJECTED, reason));
        log.info("Order {} rejected at execution: {}", order.getId(), reason);
    }
}
