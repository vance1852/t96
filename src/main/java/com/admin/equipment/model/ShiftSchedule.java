package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "shift_schedules", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"staff_id", "schedule_date"})
})
public class ShiftSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "shift_code", length = 32)
    private String shiftCode;

    @Column(name = "shift_type", nullable = false, length = 16)
    private String shiftType;

    @Column(length = 512)
    private String remark = "";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }
    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
