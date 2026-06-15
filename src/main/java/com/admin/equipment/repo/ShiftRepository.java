package com.admin.equipment.repo;

import com.admin.equipment.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Optional<Shift> findByCode(String code);
    List<Shift> findByIsActiveTrue();
    List<Shift> findByShiftType(String shiftType);
    boolean existsByCode(String code);
}
