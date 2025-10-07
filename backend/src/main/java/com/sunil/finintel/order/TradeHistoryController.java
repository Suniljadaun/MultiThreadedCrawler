package com.sunil.finintel.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunil.finintel.common.PageResponse;

@RestController
public class TradeHistoryController {

    private final TradeHistoryService tradeHistoryService;

    public TradeHistoryController(TradeHistoryService tradeHistoryService) {
        this.tradeHistoryService = tradeHistoryService;
    }

    @GetMapping("/api/v1/users/{userId}/transactions")
    public PageResponse<ExecutionView> list(@PathVariable Long userId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return tradeHistoryService.listForUser(userId, page, size);
    }
}
