package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "staff")
public class Staff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = 32)
    private String staffNo;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(length = 16)
    private String phone = "";

    @Column(nullable = false, length = 16)
    private String level = "junior";

    @Column(nullable = false, length = 64)
    private String area = "";

    @Column(name = "max_daily_hours", nullable = false)
    private Double maxDailyHours = 8.0;

    @Column(name = "max_weekly_hours", nullable = false)
    private Double maxWeeklyHours = 40.0;

    @Column(name = "max_consecutive_days", nullable = false)
    private Integer maxConsecutiveDays = 6;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "staff_skills",
        joinColumns = @JoinColumn(name = "staff_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    @OrderBy("id")
    private List<Skill> skills = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStaffNo() { return staffNo; }
    public void setStaffNo(String staffNo) { this.staffNo = staffNo; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public Double getMaxDailyHours() { return maxDailyHours; }
    public void setMaxDailyHours(Double maxDailyHours) { this.maxDailyHours = maxDailyHours; }
    public Double getMaxWeeklyHours() { return maxWeeklyHours; }
    public void setMaxWeeklyHours(Double maxWeeklyHours) { this.maxWeeklyHours = maxWeeklyHours; }
    public Integer getMaxConsecutiveDays() { return maxConsecutiveDays; }
    public void setMaxConsecutiveDays(Integer maxConsecutiveDays) { this.maxConsecutiveDays = maxConsecutiveDays; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<Skill> getSkills() { return skills; }
    public void setSkills(List<Skill> skills) { this.skills = skills; }
}
