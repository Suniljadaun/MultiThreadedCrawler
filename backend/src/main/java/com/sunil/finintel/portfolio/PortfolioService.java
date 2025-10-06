package com.sunil.finintel.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.user.UserRepository;

@Service
public class PortfolioService {

    private static final int MONEY_SCALE = 4;

    private final PositionRepository positionRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final UserRepository userRepository;

    public PortfolioService(PositionRepository positionRepository, MarketPriceRepository marketPriceRepository,
                            UserRepository userRepository) {
        this.positionRepository = positionRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.userRepository = userRepository;
    }

    // Fills join the execution's transaction (MANDATORY), so order status and position change together
    @Transactional(propagation = Propagation.MANDATORY)
    public FillResult buy(Long userId, String symbol, long qty, BigDecimal price) {
        Position position = positionRepository.findForUpdate(userId, symbol)
                .orElseGet(() -> new Position(userId, symbol));
        position.buy(qty, price);
        return FillResult.applied(positionRepository.save(position));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FillResult sell(Long userId, String symbol, long qty) {
        Optional<Position> locked = positionRepository.findForUpdate(userId, symbol);
        long held = locked.map(Position::getQuantity).orElse(0L);
        if (held < qty) {
            return FillResult.insufficient(held);
        }
        Position position = locked.get();
        position.sell(qty);
        return FillResult.applied(position);
    }

    // Values open positions at the current synthetic market price
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("user " + userId + " not found");
        }
        List<Position> positions = positionRepository.findByUserIdAndQuantityGreaterThanOrderBySymbolAsc(userId, 0);
        Map<String, BigDecimal> prices = marketPriceRepository
                .findAllById(positions.stream().map(Position::getSymbol).toList())
                .stream()
                .collect(Collectors.toMap(MarketPrice::getSymbol, MarketPrice::getPrice));

        List<PositionView> views = new ArrayList<>();
        BigDecimal totalCost = zero();
        BigDecimal totalValue = zero();
        for (Position p : positions) {
            BigDecimal qty = BigDecimal.valueOf(p.getQuantity());
            // No price known: value the position at cost rather than guessing
            BigDecimal price = prices.getOrDefault(p.getSymbol(), p.getAvgCost());
            BigDecimal cost = money(p.getAvgCost().multiply(qty));
            BigDecimal value = money(price.multiply(qty));
            views.add(new PositionView(p.getSymbol(), p.getQuantity(), p.getAvgCost(), price, value,
                    value.subtract(cost)));
            totalCost = totalCost.add(cost);
            totalValue = totalValue.add(value);
        }
        return new PortfolioResponse(userId, views, totalCost, totalValue, totalValue.subtract(totalCost));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
