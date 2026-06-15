package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final WorkOrderRepository workOrderRepo;
    private final StaffRepository staffRepo;
    private final TeamRepository teamRepo;
    private final ShiftScheduleRepository scheduleRepo;
    private final AssignmentLogRepository logRepo;

    public StatsService(WorkOrderRepository workOrderRepo, StaffRepository staffRepo,
                        TeamRepository teamRepo, ShiftScheduleRepository scheduleRepo,
                        AssignmentLogRepository logRepo) {
        this.workOrderRepo = workOrderRepo;
        this.staffRepo = staffRepo;
        this.teamRepo = teamRepo;
        this.scheduleRepo = scheduleRepo;
        this.logRepo = logRepo;
    }

    public record StaffWorkload(Long staffId, String staffName, String staffNo, String level,
                                 int activeOrderCount, double activeHours,
                                 int completedToday, int completedThisWeek, int completedThisMonth,
                                 double totalCompletedHours, double avgProcessingMinutes,
                                 double avgResponseMinutes, double avgArrivalMinutes,
                                 double loadRatio, String area) {}

    public record DispatchBoardEntry(Long staffId, String staffName, String staffNo, String teamName,
                                      String level, String area, boolean onDuty,
                                      String currentShift, int activeCount, double activeHours,
                                      double loadRatio, List<Map<String, Object>> activeOrders,
                                      double avgProcessingMinutes) {}

    public record EfficiencyReport(int periodDays, int totalCompleted, int totalCreated,
                                    double avgProcessingMinutes, double avgResponseMinutes,
                                    double avgArrivalMinutes, double onTimeRate,
                                    double loadStdDev, double loadGiniCoeff,
                                    List<StaffWorkload> staffDetails) {}

    public List<DispatchBoardEntry> getDispatchBoard(Long teamId) {
        List<Staff> staffList;
        if (teamId != null) {
            Optional<Team> t = teamRepo.findById(teamId);
            staffList = t.map(team -> team.getMembers().stream().filter(Staff::getIsActive).toList())
                    .orElse(List.of());
        } else {
            staffList = staffRepo.findByIsActiveTrue();
        }

        LocalDate today = LocalDate.now();
        List<ShiftSchedule> todaySchedules = scheduleRepo.findByScheduleDate(today);
        Map<Long, ShiftSchedule> scheduleByStaff = todaySchedules.stream()
                .collect(Collectors.toMap(ShiftSchedule::getStaffId, s -> s, (a, b) -> a));

        Map<Long, Team> teamByStaff = new HashMap<>();
        for (Team t : teamRepo.findByIsActiveTrue()) {
            for (Staff m : t.getMembers()) {
                teamByStaff.put(m.getId(), t);
            }
        }

        List<String> activeStatuses = List.of("assigned", "accepted", "arrived", "in_progress");
        List<DispatchBoardEntry> entries = new ArrayList<>();

        for (Staff s : staffList) {
            List<WorkOrder> activeOrders = workOrderRepo.findByAssigneeIdAndStatusIn(s.getId(), activeStatuses);
            double activeHours = activeOrders.stream().mapToDouble(WorkOrder::getEstimatedHours).sum();
            double maxDaily = s.getMaxDailyHours() * 5;
            double loadRatio = Math.min(1.0, activeHours / Math.max(1, maxDaily));

            ShiftSchedule sch = scheduleByStaff.get(s.getId());
            boolean onDuty = sch != null && !"rest".equals(sch.getShiftType());

            List<Map<String, Object>> orderList = activeOrders.stream().map(w -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", w.getId());
                m.put("title", w.getTitle());
                m.put("priority", w.getPriority());
                m.put("status", w.getStatus());
                m.put("estimatedHours", w.getEstimatedHours());
                m.put("createdAt", w.getCreatedAt());
                m.put("acceptedAt", w.getAcceptedAt());
                m.put("equipmentLocation", w.getEquipmentLocation());
                return m;
            }).sorted((a, b) -> {
                Map<String, Integer> pw = Map.of("urgent", 0, "high", 1, "medium", 2, "low", 3);
                return pw.getOrDefault(a.get("priority"), 99) - pw.getOrDefault(b.get("priority"), 99);
            }).toList();

            Team team = teamByStaff.get(s.getId());
            double avgProc = calculateAvgProcessingMinutes(s.getId());

            entries.add(new DispatchBoardEntry(
                    s.getId(), s.getDisplayName(), s.getStaffNo(),
                    team != null ? team.getName() : "",
                    s.getLevel(), s.getArea(), onDuty,
                    sch != null ? sch.getShiftCode() : "未排班",
                    activeOrders.size(), activeHours, loadRatio,
                    orderList, avgProc
            ));
        }

        entries.sort((a, b) -> Double.compare(b.loadRatio(), a.loadRatio()));
        return entries;
    }

    public StaffWorkload getStaffWorkload(Long staffId, LocalDate startDate, LocalDate endDate) {
        Staff s = staffRepo.findById(staffId).orElse(null);
        if (s == null) return null;

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<WorkOrder> completed = workOrderRepo.findCompletedByStaffBetween(staffId, start, end);
        List<String> activeStatuses = List.of("assigned", "accepted", "arrived", "in_progress");
        List<WorkOrder> active = workOrderRepo.findByAssigneeIdAndStatusIn(staffId, activeStatuses);

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());

        int completedToday = completedInRange(s.getId(), today, today);
        int completedWeek = completedInRange(s.getId(), weekStart, today);
        int completedMonth = completedInRange(s.getId(), monthStart, today);

        double totalHours = completed.stream().mapToDouble(WorkOrder::getEstimatedHours).sum();
        double avgProc = calculateAvgProcessingMinutes(staffId);
        double avgResp = calculateAvgResponseMinutes(staffId);
        double avgArrival = calculateAvgArrivalMinutes(staffId);

        double activeHours = active.stream().mapToDouble(WorkOrder::getEstimatedHours).sum();
        double loadRatio = Math.min(1.0, activeHours / Math.max(1, s.getMaxDailyHours() * 5));

        return new StaffWorkload(
                s.getId(), s.getDisplayName(), s.getStaffNo(), s.getLevel(),
                active.size(), activeHours,
                completedToday, completedWeek, completedMonth,
                totalHours, avgProc, avgResp, avgArrival,
                loadRatio, s.getArea()
        );
    }

    public EfficiencyReport getEfficiencyReport(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<WorkOrder> completed = workOrderRepo.findCompletedBetween(start, end);
        List<WorkOrder> allCreated = workOrderRepo.findByCreatedAtBetween(start, end);

        double totalProcMin = 0;
        double totalRespMin = 0;
        double totalArrMin = 0;
        int procCount = 0, respCount = 0, arrCount = 0;
        int onTime = 0;

        for (WorkOrder w : completed) {
            if (w.getStartedAt() != null && w.getClosedAt() != null) {
                totalProcMin += Duration.between(w.getStartedAt(), w.getClosedAt()).toMinutes();
                procCount++;
            }
            if (w.getCreatedAt() != null && w.getAcceptedAt() != null) {
                totalRespMin += Duration.between(w.getCreatedAt(), w.getAcceptedAt()).toMinutes();
                respCount++;
            }
            if (w.getAcceptedAt() != null && w.getArrivedAt() != null) {
                totalArrMin += Duration.between(w.getAcceptedAt(), w.getArrivedAt()).toMinutes();
                arrCount++;
            }
            if (w.getClosedAt() != null && w.getCreatedAt() != null) {
                long hours = Duration.between(w.getCreatedAt(), w.getClosedAt()).toHours();
                Map<String, Integer> pSLA = Map.of("urgent", 4, "high", 8, "medium", 24, "low", 72);
                int sla = pSLA.getOrDefault(w.getPriority(), 24);
                if (hours <= sla) onTime++;
            }
        }

        List<Staff> allStaff = staffRepo.findByIsActiveTrue();
        List<StaffWorkload> staffDetails = new ArrayList<>();
        Map<Long, Integer> completedCounts = new HashMap<>();
        for (Staff s : allStaff) {
            StaffWorkload sw = getStaffWorkload(s.getId(), startDate, endDate);
            if (sw != null) {
                staffDetails.add(sw);
                completedCounts.put(s.getId(),
                        completedInRange(s.getId(), startDate, endDate));
            }
        }

        double mean = completedCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = completedCounts.values().stream()
                .mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        double gini = calculateGini(new ArrayList<>(completedCounts.values()));

        return new EfficiencyReport(
                days, completed.size(), allCreated.size(),
                procCount == 0 ? 0 : totalProcMin / procCount,
                respCount == 0 ? 0 : totalRespMin / respCount,
                arrCount == 0 ? 0 : totalArrMin / arrCount,
                completed.isEmpty() ? 0 : (double) onTime / completed.size() * 100,
                stdDev, gini,
                staffDetails
        );
    }

    private int completedInRange(Long staffId, LocalDate start, LocalDate end) {
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.plusDays(1).atStartOfDay();
        return workOrderRepo.findCompletedByStaffBetween(staffId, s, e).size();
    }

    private double calculateAvgProcessingMinutes(Long staffId) {
        LocalDate start = LocalDate.now().minusDays(30);
        List<WorkOrder> orders = workOrderRepo.findCompletedByStaffBetween(
                staffId, start.atStartOfDay(), LocalDateTime.now());
        double total = 0;
        int count = 0;
        for (WorkOrder w : orders) {
            LocalDateTime from = w.getStartedAt() != null ? w.getStartedAt() : w.getAcceptedAt();
            if (from != null && w.getClosedAt() != null) {
                total += Duration.between(from, w.getClosedAt()).toMinutes();
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private double calculateAvgResponseMinutes(Long staffId) {
        LocalDate start = LocalDate.now().minusDays(30);
        List<WorkOrder> orders = workOrderRepo.findCompletedByStaffBetween(
                staffId, start.atStartOfDay(), LocalDateTime.now());
        double total = 0;
        int count = 0;
        for (WorkOrder w : orders) {
            if (w.getCreatedAt() != null && w.getAcceptedAt() != null) {
                total += Duration.between(w.getCreatedAt(), w.getAcceptedAt()).toMinutes();
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private double calculateAvgArrivalMinutes(Long staffId) {
        LocalDate start = LocalDate.now().minusDays(30);
        List<WorkOrder> orders = workOrderRepo.findCompletedByStaffBetween(
                staffId, start.atStartOfDay(), LocalDateTime.now());
        double total = 0;
        int count = 0;
        for (WorkOrder w : orders) {
            if (w.getAcceptedAt() != null && w.getArrivedAt() != null) {
                total += Duration.between(w.getAcceptedAt(), w.getArrivedAt()).toMinutes();
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private double calculateGini(List<Integer> values) {
        if (values.isEmpty()) return 0;
        Collections.sort(values);
        int n = values.size();
        long sum = values.stream().mapToLong(Integer::longValue).sum();
        if (sum == 0) return 0;

        long weightedSum = 0;
        for (int i = 0; i < n; i++) {
            weightedSum += (long) (i + 1) * values.get(i);
        }
        return (2.0 * weightedSum) / (n * sum) - (double) (n + 1) / n;
    }

    public Map<String, Object> getGrabableOrders() {
        List<WorkOrder> grabable = workOrderRepo.findGrabableOrders();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", grabable.size());
        result.put("byPriority", grabable.stream().collect(
                Collectors.groupingBy(WorkOrder::getPriority, Collectors.counting())));
        result.put("orders", grabable);
        return result;
    }

    public List<AssignmentLog> getAssignmentLogs(Long workOrderId, Long staffId) {
        if (workOrderId != null) {
            return logRepo.findByWorkOrderIdOrderByCreatedAtDesc(workOrderId);
        }
        if (staffId != null) {
            List<AssignmentLog> logs = new ArrayList<>();
            logs.addAll(logRepo.findByToStaffIdOrderByCreatedAtDesc(staffId));
            logs.addAll(logRepo.findByFromStaffIdOrderByCreatedAtDesc(staffId));
            return logs.stream()
                    .sorted(Comparator.comparing(AssignmentLog::getCreatedAt).reversed())
                    .toList();
        }
        return logRepo.findAll().stream()
                .sorted(Comparator.comparing(AssignmentLog::getCreatedAt).reversed())
                .limit(500)
                .toList();
    }
}
