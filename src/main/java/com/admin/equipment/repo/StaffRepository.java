package com.admin.equipment.repo;

import com.admin.equipment.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByStaffNo(String staffNo);
    Optional<Staff> findByUserId(Long userId);
    List<Staff> findByIsActiveTrue();
    List<Staff> findByArea(String area);

    @Query("SELECT s FROM Staff s JOIN s.skills sk WHERE sk.id = :skillId AND s.isActive = true")
    List<Staff> findBySkillId(@Param("skillId") Long skillId);

    @Query("SELECT s FROM Staff s JOIN s.skills sk WHERE sk.code = :skillCode AND s.isActive = true")
    List<Staff> findBySkillCode(@Param("skillCode") String skillCode);

    boolean existsByStaffNo(String staffNo);
    boolean existsByUserId(Long userId);
}
