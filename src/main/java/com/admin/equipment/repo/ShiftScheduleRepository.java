package com.admin.equipment.repo;

import com.admin.equipment.model.ShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, Long> {
    Optional<ShiftSchedule> findByStaffIdAndScheduleDate(Long staffId, LocalDate scheduleDate);
    List<ShiftSchedule> findByStaffIdAndScheduleDateBetween(Long staffId, LocalDate start, LocalDate end);
    List<ShiftSchedule> findByScheduleDateBetween(LocalDate start, LocalDate end);
    List<ShiftSchedule> findByScheduleDate(LocalDate date);

    @Query("SELECT s FROM ShiftSchedule s WHERE s.staffId IN :staffIds AND s.scheduleDate BETWEEN :start AND :end")
    List<ShiftSchedule> findByStaffIdsAndDateRange(@Param("staffIds") List<Long> staffIds,
                                                    @Param("start") LocalDate start,
                                                    @Param("end") LocalDate end);

    @Query("SELECT COUNT(s) > 0 FROM ShiftSchedule s WHERE s.staffId = :staffId AND s.scheduleDate = :date AND s.shiftType != 'rest'")
    boolean isStaffOnDuty(@Param("staffId") Long staffId, @Param("date") LocalDate date);
}
