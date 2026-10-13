package com.mandi.repository;

import com.mandi.domain.ImportRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    List<ImportRun> findTop30ByOrderByStartedAtDescIdDesc();

    Optional<ImportRun> findFirstByStatusNotOrderByStartedAtDescIdDesc(com.mandi.domain.ImportStatus status);
}
