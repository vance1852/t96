package com.admin.equipment.repo;

import com.admin.equipment.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByCode(String code);
    List<Team> findByIsActiveTrue();
    boolean existsByCode(String code);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE m.id = :staffId")
    List<Team> findByMemberId(@Param("staffId") Long staffId);
}
