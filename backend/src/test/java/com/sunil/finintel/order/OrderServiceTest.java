package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.sunil.finintel.common.BadRequestException;
import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.common.UnprocessableException;
import com.sunil.finintel.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private final PlaceOrderRequest request =
            new PlaceOrderRequest(1L, "acme", OrderSide.BUY, 10, new BigDecimal("101.50"));

    private Order storedOrder(PlaceOrderRequest r) {
        return new Order(1L, "ACME", OrderSide.BUY, 10, new BigDecimal("101.50"), "k1", OrderService.requestHash(r));
    }

    @Test
    void newKeyCreatesOrder() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "k1")).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PlaceOrderResult result = orderService.place("k1", request);

        assertThat(result.created()).isTrue();
        assertThat(result.order().symbol()).isEqualTo("ACME");
        assertThat(result.order().status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void sameKeySameBodyReplaysExistingOrder() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "k1")).thenReturn(Optional.of(storedOrder(request)));

        PlaceOrderResult result = orderService.place("k1", request);

        assertThat(result.created()).isFalse();
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        PlaceOrderRequest other = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 99, new BigDecimal("101.50"));
        when(userRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "k1")).thenReturn(Optional.of(storedOrder(request)));

        assertThatThrownBy(() -> orderService.place("k1", other))
                .isInstanceOf(UnprocessableException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void lostRaceReturnsWinningOrder() {
        // Both requests saw no order; the other one inserted first and our INSERT hit the unique constraint
        when(userRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "k1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(storedOrder(request)));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("uq_orders_user_idempotency"));

        PlaceOrderResult result = orderService.place("k1", request);

        assertThat(result.created()).isFalse();
    }

    @Test
    void blankKeyIsRejectedBeforeAnyDbCall() {
        assertThatThrownBy(() -> orderService.place("   ", request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(orderRepository, userRepository);
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.place("k1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hashIgnoresSymbolCaseAndTrailingZeros() {
        PlaceOrderRequest same = new PlaceOrderRequest(1L, " ACME ", OrderSide.BUY, 10, new BigDecimal("101.5"));
        assertThat(OrderService.requestHash(same)).isEqualTo(OrderService.requestHash(request));
    }

    @Test
    void getThrowsWhenMissing() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get(5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listClampsPageAndSize() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.findByUserId(eq(1L), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<Order>(List.of(), inv.getArgument(1), 0));

        orderService.listForUser(1L, -3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserId(eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(OrderService.MAX_PAGE_SIZE);
    }
}
