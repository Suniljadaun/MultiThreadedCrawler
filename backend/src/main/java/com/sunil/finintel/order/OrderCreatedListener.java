package com.sunil.finintel.order;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sunil.finintel.common.RequestIds;
import com.sunil.finintel.messaging.EventEnvelope;
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
        EventEnvelope event = eventParser.parse(record.value());
        // Same request id in the logs as the HTTP request that caused this event
        try (var ignored = RequestIds.bind(event.requestId())) {
            validationService.handleOrderCreated(event);
        }
    }
}
