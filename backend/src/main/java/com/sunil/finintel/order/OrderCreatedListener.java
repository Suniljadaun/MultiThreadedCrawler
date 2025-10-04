package com.sunil.finintel.order;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sunil.finintel.messaging.EventParser;
import com.sunil.finintel.messaging.Topics;

// Kafka entry point only; the logic lives in OrderValidationService.
// Exceptions go to the DefaultErrorHandler (retry, then DLT).
@Component
public class OrderCreatedListener {

    private final EventParser eventParser;
    private final OrderValidationService validationService;

    public OrderCreatedListener(EventParser eventParser, OrderValidationService validationService) {
        this.eventParser = eventParser;
        this.validationService = validationService;
    }

    @KafkaListener(topics = Topics.ORDERS_CREATED, groupId = OrderValidationService.CONSUMER_NAME)
    public void onMessage(ConsumerRecord<String, String> record) {
        validationService.handleOrderCreated(eventParser.parse(record.value()));
    }
}
