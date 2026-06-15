package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_orders")
public class WorkOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(nullable = false, length = 128)
    private String title;

    // 工单类型：inspection 巡检 / repair 维修 / maintenance 保养
    @Column(length = 16)
    private String type = "inspection";

    // 优先级：low / medium / high / urgent
    @Column(length = 16)
    private String priority = "medium";

    // 状态：open 待派单 / assigned 已派单 / accepted 已接单 / arrived 已到场 / in_progress 处理中 / done 已完成 / cancelled 已取消
    @Column(length = 16)
    private String status = "open";

    @Column(length = 512)
    private String description = "";

    @Column(name = "assignee", length = 64)
    private String assignee = "";

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "team_id")
    private Long teamId;

    // 所需技能ID列表，逗号分隔
    @Column(name = "required_skills", length = 256)
    private String requiredSkills = "";

    // 预计工时（小时）
    @Column(name = "estimated_hours", nullable = false)
    private Double estimatedHours = 1.0;

    // 设备位置（冗余存储，便于派工距离计算）
    @Column(name = "equipment_location", length = 128)
    private String equipmentLocation = "";

    // 设备区域
    @Column(name = "equipment_area", length = 64)
    private String equipmentArea = "";

    // 是否可抢单
    @Column(name = "can_grab", nullable = false)
    private Boolean canGrab = false;

    // 接单时间
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // 到场时间
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    // 开始处理时间
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    public String getEquipmentLocation() { return equipmentLocation; }
    public void setEquipmentLocation(String equipmentLocation) { this.equipmentLocation = equipmentLocation; }
    public String getEquipmentArea() { return equipmentArea; }
    public void setEquipmentArea(String equipmentArea) { this.equipmentArea = equipmentArea; }
    public Boolean getCanGrab() { return canGrab; }
    public void setCanGrab(Boolean canGrab) { this.canGrab = canGrab; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(LocalDateTime arrivedAt) { this.arrivedAt = arrivedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
