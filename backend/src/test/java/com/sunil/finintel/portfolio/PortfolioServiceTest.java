package com.sunil.finintel.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarketPriceRepository marketPriceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    private Position holding(long qty, String avg) {
        Position p = new Position(1L, "ACME");
        p.buy(qty, new BigDecimal(avg));
        return p;
    }

    @Test
    void firstBuyOpensPosition() {
        when(positionRepository.findForUpdate(1L, "ACME")).thenReturn(Optional.empty());
        when(positionRepository.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));

        FillResult result = portfolioService.buy(1L, "ACME", 10, new BigDecimal("100"));

        assertThat(result.applied()).isTrue();
        assertThat(result.position().getQuantity()).isEqualTo(10);
        assertThat(result.position().getAvgCost()).isEqualByComparingTo("100");
    }

    @Test
    void sellWithoutPositionIsNotApplied() {
        when(positionRepository.findForUpdate(1L, "ACME")).thenReturn(Optional.empty());

        FillResult result = portfolioService.sell(1L, "ACME", 5);

        assertThat(result.applied()).isFalse();
        assertThat(result.heldQuantity()).isZero();
    }

    @Test
    void sellMoreThanHeldIsNotApplied() {
        Position p = holding(3, "100");
        when(positionRepository.findForUpdate(1L, "ACME")).thenReturn(Optional.of(p));

        FillResult result = portfolioService.sell(1L, "ACME", 5);

        assertThat(result.applied()).isFalse();
        assertThat(result.heldQuantity()).isEqualTo(3);
        assertThat(p.getQuantity()).isEqualTo(3);
    }

    @Test
    void sellWithinHoldingReducesPosition() {
        Position p = holding(10, "100");
        when(positionRepository.findForUpdate(1L, "ACME")).thenReturn(Optional.of(p));

        FillResult result = portfolioService.sell(1L, "ACME", 4);

        assertThat(result.applied()).isTrue();
        assertThat(p.getQuantity()).isEqualTo(6);
    }

    @Test
    void portfolioIsValuedAtMarketPrice() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(positionRepository.findByUserIdAndQuantityGreaterThanOrderBySymbolAsc(1L, 0))
                .thenReturn(List.of(holding(10, "100")));
        when(marketPriceRepository.findAllById(List.of("ACME")))
                .thenReturn(List.of(new MarketPrice("ACME", new BigDecimal("112.5"))));

        PortfolioResponse portfolio = portfolioService.getPortfolio(1L);

        assertThat(portfolio.positions()).hasSize(1);
        PositionView acme = portfolio.positions().get(0);
        assertThat(acme.marketValue()).isEqualByComparingTo("1125");
        assertThat(acme.unrealizedPnl()).isEqualByComparingTo("125");
        assertThat(portfolio.totalCost()).isEqualByComparingTo("1000");
        assertThat(portfolio.totalMarketValue()).isEqualByComparingTo("1125");
        assertThat(portfolio.totalUnrealizedPnl()).isEqualByComparingTo("125");
    }

    @Test
    void unknownUserHasNoPortfolio() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> portfolioService.getPortfolio(9L)).isInstanceOf(NotFoundException.class);
    }
}
