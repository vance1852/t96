package com.admin.equipment.repo;

import com.admin.equipment.model.AssignmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentLogRepository extends JpaRepository<AssignmentLog, Long> {
    List<AssignmentLog> findByWorkOrderIdOrderByCreatedAtDesc(Long workOrderId);
    List<AssignmentLog> findByToStaffIdOrderByCreatedAtDesc(Long staffId);
    List<AssignmentLog> findByFromStaffIdOrderByCreatedAtDesc(Long staffId);
    List<AssignmentLog> findByActionOrderByCreatedAtDesc(String action);
}
