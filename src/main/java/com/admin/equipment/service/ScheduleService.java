package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final ShiftScheduleRepository scheduleRepo;
    private final StaffRepository staffRepo;
    private final ShiftRepository shiftRepo;
    private final SkillRepository skillRepo;
    private final TeamRepository teamRepo;
    private final LeaveRecordRepository leaveRepo;

    public ScheduleService(ShiftScheduleRepository scheduleRepo, StaffRepository staffRepo,
                           ShiftRepository shiftRepo, SkillRepository skillRepo,
                           TeamRepository teamRepo, LeaveRecordRepository leaveRepo) {
        this.scheduleRepo = scheduleRepo;
        this.staffRepo = staffRepo;
        this.shiftRepo = shiftRepo;
        this.skillRepo = skillRepo;
        this.teamRepo = teamRepo;
        this.leaveRepo = leaveRepo;
    }

    public record ConflictError(String type, Long staffId, String staffName, LocalDate date,
                                String shiftCode, String message) {}

    public record ScheduleValidationResult(boolean valid, List<ConflictError> errors,
                                           Map<String, Object> coverage) {}

    @Transactional(readOnly = true)
    public ScheduleValidationResult validateSchedule(List<ShiftSchedule> schedules, LocalDate startDate, LocalDate endDate) {
        List<ConflictError> errors = new ArrayList<>();
        Set<Long> staffIds = schedules.stream().map(ShiftSchedule::getStaffId).collect(Collectors.toSet());

        Map<Long, Staff> staffMap = staffRepo.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        Map<String, List<ShiftSchedule>> byStaffDate = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaffId() + "_" + s.getScheduleDate()));

        for (Map.Entry<String, List<ShiftSchedule>> entry : byStaffDate.entrySet()) {
            if (entry.getValue().size() > 1) {
                ShiftSchedule s = entry.getValue().get(0);
                Staff staff = staffMap.get(s.getStaffId());
                errors.add(new ConflictError(
                        "DUPLICATE_SHIFT",
                        s.getStaffId(),
                        staff != null ? staff.getDisplayName() : "未知",
                        s.getScheduleDate(),
                        s.getShiftCode(),
                        "同一日期存在多个排班记录"
                ));
            }
        }

        Map<Long, List<ShiftSchedule>> byStaff = schedules.stream()
                .collect(Collectors.groupingBy(ShiftSchedule::getStaffId));

        for (Map.Entry<Long, List<ShiftSchedule>> entry : byStaff.entrySet()) {
            Long staffId = entry.getKey();
            Staff staff = staffMap.get(staffId);
            if (staff == null) continue;

            List<ShiftSchedule> staffSchedules = entry.getValue().stream()
                    .sorted(Comparator.comparing(ShiftSchedule::getScheduleDate))
                    .toList();

            int consecutiveWorkDays = 0;
            LocalDate lastWorkDate = null;
            Map<LocalDate, Double> weeklyHours = new HashMap<>();

            for (ShiftSchedule s : staffSchedules) {
                boolean isRest = "rest".equals(s.getShiftType());

                if (!isRest) {
                    if (lastWorkDate != null && s.getScheduleDate().minusDays(1).equals(lastWorkDate)) {
                        consecutiveWorkDays++;
                    } else {
                        consecutiveWorkDays = 1;
                    }
                    lastWorkDate = s.getScheduleDate();

                    if (consecutiveWorkDays > staff.getMaxConsecutiveDays()) {
                        errors.add(new ConflictError(
                                "MAX_CONSECUTIVE_DAYS",
                                staffId,
                                staff.getDisplayName(),
                                s.getScheduleDate(),
                                s.getShiftCode(),
                                "连续上班天数超过上限 " + staff.getMaxConsecutiveDays() + " 天"
                        ));
                    }

                    LocalDate weekStart = s.getScheduleDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    Shift shift = findShift(s);
                    double hours = shift != null ? shift.getWorkHours() : 8.0;
                    weeklyHours.merge(weekStart, hours, Double::sum);

                    if (weeklyHours.get(weekStart) > staff.getMaxWeeklyHours()) {
                        errors.add(new ConflictError(
                                "MAX_WEEKLY_HOURS",
                                staffId,
                                staff.getDisplayName(),
                                s.getScheduleDate(),
                                s.getShiftCode(),
                                "本周工时 " + weeklyHours.get(weekStart) + " 超过上限 " + staff.getMaxWeeklyHours() + " 小时"
                        ));
                    }

                    if (hours > staff.getMaxDailyHours()) {
                        errors.add(new ConflictError(
                                "MAX_DAILY_HOURS",
                                staffId,
                                staff.getDisplayName(),
                                s.getScheduleDate(),
                                s.getShiftCode(),
                                "单班工时 " + hours + " 超过日上限 " + staff.getMaxDailyHours() + " 小时"
                        ));
                    }
                } else {
                    lastWorkDate = null;
                    consecutiveWorkDays = 0;
                }

                if (leaveRepo.isStaffOnLeave(staffId, s.getScheduleDate())) {
                    errors.add(new ConflictError(
                            "LEAVE_CONFLICT",
                            staffId,
                            staff.getDisplayName(),
                            s.getScheduleDate(),
                            s.getShiftCode(),
                            "该日期员工已有请假记录"
                    ));
                }
            }
        }

        Map<String, Object> coverage = checkSkillCoverage(schedules, startDate, endDate, staffMap);

        return new ScheduleValidationResult(errors.isEmpty(), errors, coverage);
    }

    private Shift findShift(ShiftSchedule s) {
        if (s.getShiftId() != null) {
            return shiftRepo.findById(s.getShiftId()).orElse(null);
        }
        if (s.getShiftCode() != null) {
            return shiftRepo.findByCode(s.getShiftCode()).orElse(null);
        }
        return null;
    }

    private Map<String, Object> checkSkillCoverage(List<ShiftSchedule> schedules, LocalDate startDate, LocalDate endDate,
                                                    Map<Long, Staff> staffMap) {
        Map<String, Object> result = new HashMap<>();
        List<Skill> allSkills = skillRepo.findAll();

        Map<LocalDate, Map<String, List<Staff>>> coverageByDateSkill = new TreeMap<>();
        List<String> missingCoverage = new ArrayList<>();

        Map<Long, List<Long>> staffSkills = new HashMap<>();
        for (Staff s : staffMap.values()) {
            staffSkills.put(s.getId(), s.getSkills().stream().map(Skill::getId).toList());
        }

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, List<Staff>> skillCoverage = new HashMap<>();
            for (Skill sk : allSkills) {
                skillCoverage.put(sk.getCode(), new ArrayList<>());
            }

            LocalDate d = date;
            List<ShiftSchedule> daySchedules = schedules.stream()
                    .filter(s -> s.getScheduleDate().equals(d) && !"rest".equals(s.getShiftType()))
                    .toList();

            for (ShiftSchedule ss : daySchedules) {
                Staff staff = staffMap.get(ss.getStaffId());
                if (staff == null) continue;
                List<Long> skillIds = staffSkills.getOrDefault(staff.getId(), List.of());
                for (Skill sk : allSkills) {
                    if (skillIds.contains(sk.getId())) {
                        skillCoverage.get(sk.getCode()).add(staff);
                    }
                }
            }

            coverageByDateSkill.put(date, new HashMap<>(skillCoverage));

            for (Map.Entry<String, List<Staff>> entry : skillCoverage.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    missingCoverage.add("日期 " + date + " 缺少技能: " + entry.getKey());
                }
            }
        }

        result.put("byDateSkill", coverageByDateSkill);
        result.put("missingCoverage", missingCoverage);
        result.put("hasFullCoverage", missingCoverage.isEmpty());
        return result;
    }

    @Transactional
    public List<ShiftSchedule> saveSchedules(List<ShiftSchedule> schedules) {
        List<ShiftSchedule> existing = new ArrayList<>();
        for (ShiftSchedule s : schedules) {
            Optional<ShiftSchedule> old = scheduleRepo.findByStaffIdAndScheduleDate(s.getStaffId(), s.getScheduleDate());
            old.ifPresent(existing::add);
        }
        if (!existing.isEmpty()) {
            scheduleRepo.deleteAll(existing);
        }
        return scheduleRepo.saveAll(schedules);
    }

    @Transactional(readOnly = true)
    public List<ShiftSchedule> generateWeeklyAutoSchedule(Long teamId, LocalDate weekStart) {
        Optional<Team> teamOpt = teamRepo.findById(teamId);
        if (teamOpt.isEmpty()) return List.of();

        Team team = teamOpt.get();
        List<Staff> members = team.getMembers().stream().filter(Staff::getIsActive).toList();
        if (members.isEmpty()) return List.of();

        List<Shift> workShifts = shiftRepo.findByIsActiveTrue().stream()
                .filter(s -> !"rest".equals(s.getShiftType()))
                .toList();
        if (workShifts.isEmpty()) return List.of();

        List<ShiftSchedule> schedules = new ArrayList<>();
        int memberIdx = 0;
        int[] shiftRotation = new int[members.size()];

        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;

            int restCount = Math.max(1, members.size() / (isWeekend ? 2 : 4));
            List<Integer> restIndices = new ArrayList<>();
            for (int r = 0; r < restCount; r++) {
                restIndices.add((memberIdx + r) % members.size());
            }
            memberIdx = (memberIdx + 1) % members.size();

            for (int i = 0; i < members.size(); i++) {
                Staff m = members.get(i);
                ShiftSchedule s = new ShiftSchedule();
                s.setStaffId(m.getId());
                s.setScheduleDate(date);

                if (restIndices.contains(i)) {
                    s.setShiftType("rest");
                    s.setShiftCode("REST");
                } else {
                    Shift shift = workShifts.get(shiftRotation[i] % workShifts.size());
                    s.setShiftType(shift.getShiftType());
                    s.setShiftCode(shift.getCode());
                    s.setShiftId(shift.getId());
                    shiftRotation[i]++;
                }
                schedules.add(s);
            }
        }
        return schedules;
    }

    @Transactional(readOnly = true)
    public Map<Long, Staff> getOnDutyStaffAt(LocalDate date, String shiftType) {
        List<ShiftSchedule> schedules;
        if (shiftType == null) {
            schedules = scheduleRepo.findByScheduleDate(date).stream()
                    .filter(s -> !"rest".equals(s.getShiftType()))
                    .toList();
        } else {
            schedules = scheduleRepo.findByScheduleDate(date).stream()
                    .filter(s -> shiftType.equals(s.getShiftType()))
                    .toList();
        }
        Set<Long> onDutyIds = schedules.stream()
                .filter(s -> !leaveRepo.isStaffOnLeave(s.getStaffId(), date))
                .map(ShiftSchedule::getStaffId)
                .collect(Collectors.toSet());
        return staffRepo.findAllById(onDutyIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));
    }
}
