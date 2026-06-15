package com.admin.equipment.repo;

import com.admin.equipment.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    List<WorkOrder> findAllByOrderByIdDesc();
    List<WorkOrder> findByEquipmentIdOrderByIdDesc(Long equipmentId);
    List<WorkOrder> findByStatusOrderByIdDesc(String status);
    long countByStatus(String status);

    List<WorkOrder> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

    @Query("SELECT w FROM WorkOrder w WHERE w.assigneeId = :staffId AND w.status IN :statuses ORDER BY w.createdAt DESC")
    List<WorkOrder> findByAssigneeIdAndStatusIn(@Param("staffId") Long staffId, @Param("statuses") List<String> statuses);

    @Query("SELECT w FROM WorkOrder w WHERE w.status IN :statuses ORDER BY " +
           "CASE w.priority WHEN 'urgent' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END, w.createdAt ASC")
    List<WorkOrder> findPendingOrderByPriority(@Param("statuses") List<String> statuses);

    @Query("SELECT w FROM WorkOrder w WHERE w.status = 'open' AND w.canGrab = true ORDER BY " +
           "CASE w.priority WHEN 'urgent' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END, w.createdAt ASC")
    List<WorkOrder> findGrabableOrders();

    @Query("SELECT COALESCE(SUM(w.estimatedHours), 0) FROM WorkOrder w WHERE w.assigneeId = :staffId " +
           "AND w.status IN ('assigned', 'accepted', 'arrived', 'in_progress')")
    Double sumAssignedHoursForStaff(@Param("staffId") Long staffId);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.assigneeId = :staffId " +
           "AND w.status IN ('assigned', 'accepted', 'arrived', 'in_progress')")
    Long countActiveOrdersForStaff(@Param("staffId") Long staffId);

    @Query("SELECT w FROM WorkOrder w WHERE w.createdAt BETWEEN :start AND :end")
    List<WorkOrder> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT w FROM WorkOrder w WHERE w.closedAt BETWEEN :start AND :end AND w.status = 'done'")
    List<WorkOrder> findCompletedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT w FROM WorkOrder w WHERE w.assigneeId = :staffId AND w.closedAt BETWEEN :start AND :end AND w.status = 'done'")
    List<WorkOrder> findCompletedByStaffBetween(@Param("staffId") Long staffId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}
