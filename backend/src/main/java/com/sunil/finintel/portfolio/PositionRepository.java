package com.sunil.finintel.portfolio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PositionRepository extends JpaRepository<Position, Long> {

    // SELECT ... FOR UPDATE: two fills for the same position wait for each other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Position p where p.userId = :userId and p.symbol = :symbol")
    Optional<Position> findForUpdate(@Param("userId") Long userId, @Param("symbol") String symbol);

    List<Position> findByUserIdAndQuantityGreaterThanOrderBySymbolAsc(Long userId, long quantity);

    // Users whose cached portfolio depends on this symbol's price
    @Query("select distinct p.userId from Position p where p.symbol = :symbol and p.quantity > 0")
    List<Long> findHolderIds(@Param("symbol") String symbol);
}
