package com.mandi.repository;

import com.mandi.domain.WatchItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchItemRepository extends JpaRepository<WatchItem, Long> {

    @Query("select w from WatchItem w join fetch w.commodity join fetch w.market where w.userId = :userId order by w.createdAt, w.id")
    List<WatchItem> findForUser(@Param("userId") Long userId);

    Optional<WatchItem> findByUserIdAndCommodityIdAndMarketId(Long userId, Long commodityId, Long marketId);

    Optional<WatchItem> findByIdAndUserId(Long id, Long userId);
}
