package com.mandi.repository;

import com.mandi.domain.Commodity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommodityRepository extends JpaRepository<Commodity, Long> {

    Optional<Commodity> findByNameIgnoreCase(String name);
}
