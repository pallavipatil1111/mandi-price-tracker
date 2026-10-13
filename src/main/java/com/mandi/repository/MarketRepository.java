package com.mandi.repository;

import com.mandi.domain.Market;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, Long> {

    Optional<Market> findFirstByNameIgnoreCase(String name);
}
