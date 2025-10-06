package com.sunil.finintel.portfolio;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/market-prices")
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    public MarketPriceController(MarketPriceService marketPriceService) {
        this.marketPriceService = marketPriceService;
    }

    @GetMapping
    public List<MarketPriceResponse> list() {
        return marketPriceService.list();
    }

    @PutMapping("/{symbol}")
    public MarketPriceResponse update(@PathVariable String symbol, @Valid @RequestBody UpdatePriceRequest request) {
        return marketPriceService.update(symbol, request.price());
    }
}
