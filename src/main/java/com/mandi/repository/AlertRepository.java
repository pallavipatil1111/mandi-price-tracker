package com.mandi.repository;

import com.mandi.domain.Alert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("select a from Alert a join fetch a.commodity join fetch a.market where a.userId = :userId order by a.createdAt desc, a.id desc")
    List<Alert> findForUser(@Param("userId") Long userId);

    @Query("""
            select a from Alert a join fetch a.commodity join fetch a.market
            where a.active = true and a.commodity.id = :commodityId and a.market.id = :marketId""")
    List<Alert> findActiveForSeries(@Param("commodityId") Long commodityId, @Param("marketId") Long marketId);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
