package com.admin.equipment.repo;

import com.admin.equipment.model.LeaveRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, Long> {
    List<LeaveRecord> findByStaffId(Long staffId);
    List<LeaveRecord> findByStatus(String status);

    @Query("SELECT l FROM LeaveRecord l WHERE l.staffId = :staffId AND l.status = 'approved' " +
           "AND l.startDate <= :date AND l.endDate >= :date")
    List<LeaveRecord> findActiveLeaveForStaff(@Param("staffId") Long staffId, @Param("date") LocalDate date);

    @Query("SELECT l FROM LeaveRecord l WHERE l.status = 'approved' AND l.startDate <= :end AND l.endDate >= :start")
    List<LeaveRecord> findApprovedInDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(l) > 0 FROM LeaveRecord l WHERE l.staffId = :staffId AND l.status = 'approved' " +
           "AND l.startDate <= :date AND l.endDate >= :date")
    boolean isStaffOnLeave(@Param("staffId") Long staffId, @Param("date") LocalDate date);
}
