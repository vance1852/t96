package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_logs")
public class AssignmentLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "from_staff_id")
    private Long fromStaffId;

    @Column(name = "to_staff_id")
    private Long toStaffId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(length = 512)
    private String reason = "";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getFromStaffId() { return fromStaffId; }
    public void setFromStaffId(Long fromStaffId) { this.fromStaffId = fromStaffId; }
    public Long getToStaffId() { return toStaffId; }
    public void setToStaffId(Long toStaffId) { this.toStaffId = toStaffId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
