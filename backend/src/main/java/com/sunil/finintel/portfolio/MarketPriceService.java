package com.sunil.finintel.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.common.NotFoundException;

@Service
public class MarketPriceService {

    private final MarketPriceRepository marketPriceRepository;

    public MarketPriceService(MarketPriceRepository marketPriceRepository) {
        this.marketPriceRepository = marketPriceRepository;
    }

    @Transactional(readOnly = true)
    public List<MarketPriceResponse> list() {
        return marketPriceRepository.findAll(Sort.by("symbol")).stream().map(MarketPriceResponse::from).toList();
    }

    // Simulation helper: move a synthetic price. Only existing symbols can be changed.
    @Transactional
    public MarketPriceResponse update(String symbol, BigDecimal price) {
        MarketPrice marketPrice = marketPriceRepository.findById(symbol.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("symbol " + symbol + " not found"));
        marketPrice.changePrice(price);
        return MarketPriceResponse.from(marketPriceRepository.saveAndFlush(marketPrice));
    }
}
