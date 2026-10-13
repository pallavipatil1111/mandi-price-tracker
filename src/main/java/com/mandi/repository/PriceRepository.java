package com.mandi.repository;

import com.mandi.domain.Price;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceRepository extends JpaRepository<Price, Long> {

    /** Existing rows for the dates being imported, so the import can update instead of duplicate. */
    @Query("select p from Price p join fetch p.market join fetch p.commodity where p.arrivalDate in :dates")
    List<Price> findForDates(@Param("dates") Collection<LocalDate> dates);

    @Query("""
            select new com.mandi.repository.DailyRow(p.arrivalDate, min(p.minPrice), max(p.maxPrice), avg(p.modalPrice))
            from Price p
            where p.commodity.id = :commodityId and p.market.id = :marketId and p.arrivalDate >= :from
            group by p.arrivalDate
            order by p.arrivalDate""")
    List<DailyRow> series(@Param("commodityId") Long commodityId, @Param("marketId") Long marketId,
                          @Param("from") LocalDate from);

    @Query("""
            select new com.mandi.repository.BoardRow(c.id, c.name, c.category, m.id, m.name, m.district, m.state,
                   p.arrivalDate, min(p.minPrice), max(p.maxPrice), avg(p.modalPrice))
            from Price p join p.commodity c join p.market m
            where p.arrivalDate >= :from
            group by c.id, c.name, c.category, m.id, m.name, m.district, m.state, p.arrivalDate
            order by c.name, m.name, p.arrivalDate""")
    List<BoardRow> board(@Param("from") LocalDate from);

    @Query("""
            select new com.mandi.repository.BoardRow(c.id, c.name, c.category, m.id, m.name, m.district, m.state,
                   p.arrivalDate, min(p.minPrice), max(p.maxPrice), avg(p.modalPrice))
            from Price p join p.commodity c join p.market m
            where c.id = :commodityId and p.arrivalDate >= :from
            group by c.id, c.name, c.category, m.id, m.name, m.district, m.state, p.arrivalDate
            order by m.name, p.arrivalDate""")
    List<BoardRow> boardForCommodity(@Param("commodityId") Long commodityId, @Param("from") LocalDate from);

    @Query("""
            select new com.mandi.repository.SeriesRef(c.id, c.name, c.category, m.id, m.name, m.district, m.state,
                   min(p.arrivalDate), max(p.arrivalDate), count(distinct p.arrivalDate))
            from Price p join p.commodity c join p.market m
            group by c.id, c.name, c.category, m.id, m.name, m.district, m.state
            order by c.name, m.name""")
    List<SeriesRef> seriesIndex();

    @Query("select max(p.arrivalDate) from Price p")
    LocalDate latestDate();

    @Query("select max(p.arrivalDate) from Price p where p.commodity.id = :commodityId and p.market.id = :marketId")
    LocalDate latestDate(@Param("commodityId") Long commodityId, @Param("marketId") Long marketId);
}
