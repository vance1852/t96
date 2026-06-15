package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    private final WorkOrderRepository workOrderRepo;
    private final StaffRepository staffRepo;
    private final TeamRepository teamRepo;
    private final ShiftScheduleRepository scheduleRepo;
    private final LeaveRecordRepository leaveRepo;
    private final AssignmentLogRepository logRepo;
    private final EquipmentRepository equipmentRepo;

    public DispatchService(WorkOrderRepository workOrderRepo, StaffRepository staffRepo,
                           TeamRepository teamRepo, ShiftScheduleRepository scheduleRepo,
                           LeaveRecordRepository leaveRepo, AssignmentLogRepository logRepo,
                           EquipmentRepository equipmentRepo) {
        this.workOrderRepo = workOrderRepo;
        this.staffRepo = staffRepo;
        this.teamRepo = teamRepo;
        this.scheduleRepo = scheduleRepo;
        this.leaveRepo = leaveRepo;
        this.logRepo = logRepo;
        this.equipmentRepo = equipmentRepo;
    }

    public record CandidateScore(Long staffId, String staffName, double score,
                                  double skillScore, double distanceScore,
                                  double loadScore, double levelScore,
                                  double currentLoad, double loadRatio) {}

    public record DispatchResult(Long workOrderId, Long assignedStaffId, String assignedStaffName,
                                  double score, List<CandidateScore> allCandidates, boolean success,
                                  String reason) {}

    public record BatchDispatchResult(List<DispatchResult> results, int totalCount,
                                       int successCount, int failCount,
                                       double avgResponseScore, double loadStdDev,
                                       Map<String, Object> metrics) {}

    public record AlgorithmComparison(BatchDispatchResult smartResult, BatchDispatchResult baselineResult,
                                       Map<String, Object> comparison) {}

    private static final Map<String, Integer> PRIORITY_WEIGHT = Map.of(
            "urgent", 100, "high", 60, "medium", 30, "low", 10
    );

    private static final Map<String, Double> LEVEL_WEIGHT = Map.of(
            "expert", 1.5, "senior", 1.25, "intermediate", 1.0,
            "junior", 0.8, "trainee", 0.5
    );

    private static final double W_SKILL = 0.35;
    private static final double W_DISTANCE = 0.25;
    private static final double W_LOAD = 0.25;
    private static final double W_LEVEL = 0.15;

    @Transactional
    public DispatchResult dispatchSingle(Long workOrderId, Long operatorId) {
        WorkOrder wo = workOrderRepo.findById(workOrderId).orElse(null);
        if (wo == null) return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "工单不存在");
        if (!"open".equals(wo.getStatus())) {
            return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "工单状态不是待派单");
        }

        List<CandidateScore> candidates = scoreCandidates(wo);
        if (candidates.isEmpty()) {
            return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "无符合条件的候选人");
        }

        CandidateScore best = candidates.get(0);
        assignWorkOrder(wo, best.staffId(), operatorId, "auto_smart");

        return new DispatchResult(workOrderId, best.staffId(), best.staffName(),
                best.score(), candidates, true, null);
    }

    @Transactional
    public BatchDispatchResult dispatchBatch(List<Long> workOrderIds, Long operatorId) {
        List<WorkOrder> orders = workOrderRepo.findAllById(workOrderIds).stream()
                .filter(w -> "open".equals(w.getStatus()))
                .sorted(Comparator.comparingInt((WorkOrder w) -> PRIORITY_WEIGHT.getOrDefault(w.getPriority(), 10)).reversed()
                        .thenComparing(WorkOrder::getCreatedAt))
                .toList();

        List<DispatchResult> results = new ArrayList<>();
        Map<Long, Double> tempLoad = new HashMap<>();

        for (WorkOrder wo : orders) {
            List<CandidateScore> candidates = scoreCandidatesWithTempLoad(wo, tempLoad);
            if (candidates.isEmpty()) {
                results.add(new DispatchResult(wo.getId(), null, null, 0, List.of(), false, "无符合条件的候选人"));
                continue;
            }
            CandidateScore best = candidates.get(0);
            assignWorkOrder(wo, best.staffId(), operatorId, "auto_smart_batch");
            tempLoad.merge(best.staffId(), wo.getEstimatedHours(), Double::sum);
            results.add(new DispatchResult(wo.getId(), best.staffId(), best.staffName(),
                    best.score(), candidates, true, null));
        }

        return buildBatchResult(results, orders.size());
    }

    @Transactional
    public BatchDispatchResult dispatchAllOpen(Long operatorId) {
        List<Long> openIds = workOrderRepo.findByStatusOrderByIdDesc("open").stream()
                .map(WorkOrder::getId).toList();
        return dispatchBatch(openIds, operatorId);
    }

    public AlgorithmComparison compareAlgorithms(List<Long> workOrderIds) {
        List<WorkOrder> orders = workOrderRepo.findAllById(workOrderIds).stream()
                .filter(w -> "open".equals(w.getStatus()))
                .toList();

        BatchDispatchResult smart = simulateSmartDispatch(orders);
        BatchDispatchResult baseline = simulateBaselineDispatch(orders);

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("smartSuccessRate", smart.successCount() * 100.0 / Math.max(1, smart.totalCount()));
        comparison.put("baselineSuccessRate", baseline.successCount() * 100.0 / Math.max(1, baseline.totalCount()));
        comparison.put("smartAvgScore", smart.avgResponseScore());
        comparison.put("smartLoadStdDev", smart.loadStdDev());
        comparison.put("baselineLoadStdDev", baseline.loadStdDev());
        comparison.put("loadBalanceImprovement", baseline.loadStdDev() == 0 ? 0 :
                (baseline.loadStdDev() - smart.loadStdDev()) / baseline.loadStdDev() * 100);
        comparison.put("workOrders", orders.size());
        comparison.put("description", "智能派工相对基线算法的负载均衡改善百分比");

        return new AlgorithmComparison(smart, baseline, comparison);
    }

    private List<CandidateScore> scoreCandidates(WorkOrder wo) {
        return scoreCandidatesWithTempLoad(wo, new HashMap<>());
    }

    private List<CandidateScore> scoreCandidatesWithTempLoad(WorkOrder wo, Map<Long, Double> tempLoad) {
        LocalDate today = LocalDate.now();

        List<ShiftSchedule> todaySchedules = scheduleRepo.findByScheduleDate(today).stream()
                .filter(s -> !"rest".equals(s.getShiftType()))
                .filter(s -> !leaveRepo.isStaffOnLeave(s.getStaffId(), today))
                .toList();

        Set<Long> onDutyIds = todaySchedules.stream().map(ShiftSchedule::getStaffId).collect(Collectors.toSet());
        if (onDutyIds.isEmpty()) return List.of();

        List<Staff> onDutyStaff = staffRepo.findAllById(onDutyIds).stream()
                .filter(Staff::getIsActive)
                .toList();

        Set<Long> requiredSkillIds = parseSkillIds(wo.getRequiredSkills());

        Map<Long, Set<Long>> staffSkills = new HashMap<>();
        for (Staff s : onDutyStaff) {
            staffSkills.put(s.getId(), s.getSkills().stream().map(Skill::getId).collect(Collectors.toSet()));
        }

        List<CandidateScore> scored = new ArrayList<>();
        for (Staff staff : onDutyStaff) {
            Set<Long> mySkills = staffSkills.getOrDefault(staff.getId(), Set.of());
            if (!requiredSkillIds.isEmpty() && !mySkills.containsAll(requiredSkillIds)) {
                continue;
            }

            double skillScore;
            if (requiredSkillIds.isEmpty()) {
                skillScore = 0.8;
            } else {
                long matchCount = requiredSkillIds.stream().filter(mySkills::contains).count();
                skillScore = (double) matchCount / requiredSkillIds.size();
            }

            double distanceScore = calculateDistanceScore(staff, wo);

            double dbLoad = workOrderRepo.sumAssignedHoursForStaff(staff.getId());
            double temp = tempLoad.getOrDefault(staff.getId(), 0.0);
            double totalLoad = dbLoad + temp;
            double maxHours = staff.getMaxDailyHours() * 5;
            double loadRatio = Math.min(1.0, totalLoad / Math.max(1, maxHours));
            double loadScore = 1.0 - loadRatio;

            double levelScore = LEVEL_WEIGHT.getOrDefault(staff.getLevel(), 1.0);
            String priority = wo.getPriority();
            if ("urgent".equals(priority) || "high".equals(priority)) {
                levelScore = levelScore > 1.0 ? levelScore : levelScore * 1.2;
            } else {
                levelScore = levelScore < 1.0 ? levelScore : 1.0;
            }

            double score = W_SKILL * skillScore + W_DISTANCE * distanceScore
                    + W_LOAD * loadScore + W_LEVEL * (levelScore / 1.5);
            score = score * (PRIORITY_WEIGHT.getOrDefault(priority, 10) / 100.0 + 0.5);

            scored.add(new CandidateScore(staff.getId(), staff.getDisplayName(), score,
                    skillScore, distanceScore, loadScore, levelScore, totalLoad, loadRatio));
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored;
    }

    private double calculateDistanceScore(Staff staff, WorkOrder wo) {
        String staffArea = staff.getArea() == null ? "" : staff.getArea();
        String eqArea = wo.getEquipmentArea() == null ? "" : wo.getEquipmentArea();
        String eqLoc = wo.getEquipmentLocation() == null ? "" : wo.getEquipmentLocation();

        if (staffArea.equals(eqArea) && !staffArea.isBlank()) return 1.0;
        if (!staffArea.isBlank() && !eqArea.isBlank() &&
                (staffArea.contains(eqArea) || eqArea.contains(staffArea))) return 0.8;
        if (!staffArea.isBlank() && !eqLoc.isBlank() && eqLoc.contains(staffArea)) return 0.6;
        if (!staffArea.isBlank() && !eqArea.isBlank()) {
            String[] s1 = staffArea.split("[区-]");
            String[] s2 = eqArea.split("[区-]");
            for (String p1 : s1) {
                for (String p2 : s2) {
                    if (p1.equals(p2) && !p1.isBlank()) return 0.5;
                }
            }
        }
        return 0.3;
    }

    private Set<Long> parseSkillIds(String skillStr) {
        if (skillStr == null || skillStr.isBlank()) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (String s : skillStr.split("[,;，；]")) {
            s = s.trim();
            if (!s.isEmpty()) {
                try {
                    ids.add(Long.parseLong(s));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }

    private void assignWorkOrder(WorkOrder wo, Long staffId, Long operatorId, String action) {
        Staff staff = staffRepo.findById(staffId).orElse(null);
        Long oldAssignee = wo.getAssigneeId();

        wo.setAssigneeId(staffId);
        wo.setAssignee(staff != null ? staff.getDisplayName() : "");
        wo.setStatus("assigned");

        List<Team> teams = teamRepo.findByMemberId(staffId);
        if (!teams.isEmpty()) {
            wo.setTeamId(teams.get(0).getId());
        }

        workOrderRepo.save(wo);

        AssignmentLog log = new AssignmentLog();
        log.setWorkOrderId(wo.getId());
        log.setFromStaffId(oldAssignee);
        log.setToStaffId(staffId);
        log.setAction(action);
        log.setOperatorId(operatorId);
        logRepo.save(log);
    }

    private BatchDispatchResult simulateSmartDispatch(List<WorkOrder> orders) {
        List<WorkOrder> sortedOrders = orders.stream()
                .sorted(Comparator.comparingInt((WorkOrder w) -> PRIORITY_WEIGHT.getOrDefault(w.getPriority(), 10)).reversed()
                        .thenComparing(WorkOrder::getCreatedAt))
                .toList();

        List<DispatchResult> results = new ArrayList<>();
        Map<Long, Double> tempLoad = new HashMap<>();

        for (WorkOrder wo : sortedOrders) {
            List<CandidateScore> candidates = scoreCandidatesWithTempLoad(wo, tempLoad);
            if (candidates.isEmpty()) {
                results.add(new DispatchResult(wo.getId(), null, null, 0, List.of(), false, "无候选人"));
                continue;
            }
            CandidateScore best = candidates.get(0);
            tempLoad.merge(best.staffId(), wo.getEstimatedHours(), Double::sum);
            results.add(new DispatchResult(wo.getId(), best.staffId(), best.staffName(),
                    best.score(), candidates, true, null));
        }
        return buildBatchResult(results, sortedOrders.size());
    }

    private BatchDispatchResult simulateBaselineDispatch(List<WorkOrder> orders) {
        List<DispatchResult> results = new ArrayList<>();
        Map<Long, Double> tempLoad = new HashMap<>();
        LocalDate today = LocalDate.now();

        Set<Long> onDutyIds = scheduleRepo.findByScheduleDate(today).stream()
                .filter(s -> !"rest".equals(s.getShiftType()))
                .filter(s -> !leaveRepo.isStaffOnLeave(s.getStaffId(), today))
                .map(ShiftSchedule::getStaffId)
                .collect(Collectors.toSet());

        for (WorkOrder wo : orders) {
            Staff bestStaff = null;
            double minLoad = Double.MAX_VALUE;

            for (Long sid : onDutyIds) {
                Optional<Staff> opt = staffRepo.findById(sid);
                if (opt.isEmpty()) continue;
                Staff s = opt.get();
                Set<Long> mySkills = s.getSkills().stream().map(Skill::getId).collect(Collectors.toSet());
                Set<Long> required = parseSkillIds(wo.getRequiredSkills());
                if (!required.isEmpty() && !mySkills.containsAll(required)) continue;

                double load = workOrderRepo.sumAssignedHoursForStaff(sid)
                        + tempLoad.getOrDefault(sid, 0.0);
                if (load < minLoad) {
                    minLoad = load;
                    bestStaff = s;
                }
            }

            if (bestStaff == null) {
                results.add(new DispatchResult(wo.getId(), null, null, 0, List.of(), false, "无候选人"));
            } else {
                tempLoad.merge(bestStaff.getId(), wo.getEstimatedHours(), Double::sum);
                results.add(new DispatchResult(wo.getId(), bestStaff.getId(), bestStaff.getDisplayName(),
                        minLoad, List.of(), true, null));
            }
        }
        return buildBatchResult(results, orders.size());
    }

    private BatchDispatchResult buildBatchResult(List<DispatchResult> results, int total) {
        int success = (int) results.stream().filter(DispatchResult::success).count();
        int fail = results.size() - success;

        double avgScore = results.stream()
                .filter(DispatchResult::success)
                .mapToDouble(DispatchResult::score)
                .average().orElse(0);

        Map<Long, Double> loads = new HashMap<>();
        for (DispatchResult r : results) {
            if (r.success() && r.assignedStaffId() != null) {
                loads.merge(r.assignedStaffId(), 1.0, Double::sum);
            }
        }
        double mean = loads.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = loads.values().stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("loadDistribution", loads);
        metrics.put("avgPerPerson", mean);
        metrics.put("maxPerPerson", loads.values().stream().mapToDouble(Double::doubleValue).max().orElse(0));
        metrics.put("minPerPerson", loads.values().stream().mapToDouble(Double::doubleValue).min().orElse(0));

        return new BatchDispatchResult(results, total, success, fail, avgScore, stdDev, metrics);
    }

    @Transactional
    public DispatchResult manualAssign(Long workOrderId, Long staffId, Long operatorId, String reason) {
        WorkOrder wo = workOrderRepo.findById(workOrderId).orElse(null);
        if (wo == null) return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "工单不存在");

        if (staffId != null) {
            Optional<Staff> s = staffRepo.findById(staffId);
            if (s.isEmpty()) {
                return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "人员不存在");
            }
        }

        assignWorkOrder(wo, staffId, operatorId, "manual_assign");
        Staff staff = staffId != null ? staffRepo.findById(staffId).orElse(null) : null;
        return new DispatchResult(workOrderId, staffId,
                staff != null ? staff.getDisplayName() : null, 0, List.of(), true, reason);
    }

    @Transactional
    public DispatchResult grabOrder(Long workOrderId, Long staffId) {
        WorkOrder wo = workOrderRepo.findById(workOrderId).orElse(null);
        if (wo == null) return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "工单不存在");
        if (!"open".equals(wo.getStatus()) || !Boolean.TRUE.equals(wo.getCanGrab())) {
            return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "该工单不可抢单");
        }
        Staff staff = staffRepo.findById(staffId).orElse(null);
        if (staff == null) return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "人员不存在");

        Set<Long> required = parseSkillIds(wo.getRequiredSkills());
        Set<Long> mySkills = staff.getSkills().stream().map(Skill::getId).collect(Collectors.toSet());
        if (!required.isEmpty() && !mySkills.containsAll(required)) {
            return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "技能不匹配");
        }

        assignWorkOrder(wo, staffId, staffId, "grab");
        return new DispatchResult(workOrderId, staffId, staff.getDisplayName(), 0, List.of(), true, "抢单成功");
    }

    @Transactional
    public DispatchResult returnOrder(Long workOrderId, Long staffId, String reason) {
        WorkOrder wo = workOrderRepo.findById(workOrderId).orElse(null);
        if (wo == null) return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "工单不存在");
        if (!Objects.equals(wo.getAssigneeId(), staffId)) {
            return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "非该工单处理人");
        }
        if ("done".equals(wo.getStatus()) || "cancelled".equals(wo.getStatus())) {
            return new DispatchResult(workOrderId, null, null, 0, List.of(), false, "工单已完成/取消，无法退单");
        }

        Long oldId = wo.getAssigneeId();
        String oldName = wo.getAssignee();

        wo.setAssigneeId(null);
        wo.setAssignee("");
        wo.setTeamId(null);
        wo.setStatus("open");
        wo.setAcceptedAt(null);
        wo.setArrivedAt(null);
        wo.setStartedAt(null);
        workOrderRepo.save(wo);

        AssignmentLog log = new AssignmentLog();
        log.setWorkOrderId(wo.getId());
        log.setFromStaffId(oldId);
        log.setToStaffId(null);
        log.setAction("return");
        log.setOperatorId(staffId);
        log.setReason(reason);
        logRepo.save(log);

        return new DispatchResult(workOrderId, null, null, 0, List.of(), true,
                oldName + " 退单成功: " + reason);
    }

    @Transactional
    public List<DispatchResult> reassignForLeave(Long staffId, LocalDate startDate, LocalDate endDate, Long operatorId) {
        List<String> activeStatuses = List.of("assigned", "accepted", "arrived", "in_progress");
        List<WorkOrder> activeOrders = workOrderRepo.findByAssigneeIdAndStatusIn(staffId, activeStatuses);
        if (activeOrders.isEmpty()) return List.of();

        List<DispatchResult> results = new ArrayList<>();
        for (WorkOrder wo : activeOrders) {
            List<CandidateScore> candidates = scoreCandidates(wo).stream()
                    .filter(c -> !Objects.equals(c.staffId(), staffId))
                    .toList();
            if (candidates.isEmpty()) {
                wo.setStatus("open");
                wo.setAssigneeId(null);
                wo.setAssignee("");
                wo.setTeamId(null);
                wo.setCanGrab(true);
                workOrderRepo.save(wo);
                results.add(new DispatchResult(wo.getId(), null, null, 0, List.of(),
                        false, "无替代人员，已转为抢单池"));
                continue;
            }
            CandidateScore best = candidates.get(0);
            assignWorkOrder(wo, best.staffId(), operatorId, "reassign_leave");
            results.add(new DispatchResult(wo.getId(), best.staffId(), best.staffName(),
                    best.score(), candidates, true, "请假重分派"));
        }
        return results;
    }

    @Transactional
    public void updateWorkOrderTiming(Long workOrderId, String action) {
        WorkOrder wo = workOrderRepo.findById(workOrderId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case "accept" -> {
                wo.setStatus("accepted");
                wo.setAcceptedAt(now);
            }
            case "arrive" -> {
                wo.setStatus("arrived");
                wo.setArrivedAt(now);
            }
            case "start" -> {
                wo.setStatus("in_progress");
                wo.setStartedAt(now);
            }
            case "complete" -> {
                wo.setStatus("done");
                wo.setClosedAt(now);
            }
            default -> throw new IllegalArgumentException("未知操作: " + action);
        }
        workOrderRepo.save(wo);
    }
}
