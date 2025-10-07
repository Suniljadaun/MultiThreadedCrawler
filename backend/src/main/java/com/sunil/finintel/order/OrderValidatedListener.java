package com.sunil.finintel.order;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sunil.finintel.messaging.EventParser;
import com.sunil.finintel.messaging.Topics;

// Kafka entry point only; the logic lives in OrderExecutionService
@Component
public class OrderValidatedListener {

    private final EventParser eventParser;
    private final OrderExecutionService executionService;

    public OrderValidatedListener(EventParser eventParser, OrderExecutionService executionService) {
        this.eventParser = eventParser;
        this.executionService = executionService;
    }

    @KafkaListener(topics = Topics.ORDERS_VALIDATED, groupId = OrderExecutionService.CONSUMER_NAME)
    public void onMessage(ConsumerRecord<String, String> record) {
        executionService.handleOrderValidated(eventParser.parse(record.value()));
    }
}
