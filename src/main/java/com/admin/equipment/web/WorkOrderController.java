package com.admin.equipment.web;

import com.admin.equipment.model.Equipment;
import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.repo.EquipmentRepository;
import com.admin.equipment.repo.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private static final Set<String> TYPES = Set.of("inspection", "repair", "maintenance");
    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "urgent");
    private static final Set<String> STATUSES = Set.of(
            "open", "assigned", "accepted", "arrived", "in_progress", "done", "cancelled");

    private final WorkOrderRepository repo;
    private final EquipmentRepository equipmentRepo;

    public WorkOrderController(WorkOrderRepository repo, EquipmentRepository equipmentRepo) {
        this.repo = repo;
        this.equipmentRepo = equipmentRepo;
    }

    public record WorkOrderRequest(Long equipmentId, String title, String type, String priority,
                                   String description, String assignee, Long assigneeId,
                                   String requiredSkills, Double estimatedHours,
                                   String equipmentLocation, String equipmentArea,
                                   Boolean canGrab) {}

    public record StatusRequest(String status) {}

    @GetMapping
    public List<WorkOrder> list(@RequestParam(required = false) Long equipmentId,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) Long assigneeId) {
        if (equipmentId != null) {
            return repo.findByEquipmentIdOrderByIdDesc(equipmentId);
        }
        if (status != null) {
            return repo.findByStatusOrderByIdDesc(status);
        }
        if (assigneeId != null) {
            return repo.findByAssigneeIdOrderByCreatedAtDesc(assigneeId);
        }
        return repo.findAllByOrderByIdDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "工单不存在")));
    }

    @GetMapping("/pending-by-priority")
    public ResponseEntity<?> listPendingByPriority() {
        List<String> pending = List.of("open");
        return ResponseEntity.ok(repo.findPendingOrderByPriority(pending));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WorkOrderRequest req) {
        if (req.equipmentId() == null || req.title() == null || req.title().isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "设备和标题必填"));
        }
        Equipment eq = equipmentRepo.findById(req.equipmentId()).orElse(null);
        if (eq == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "设备不存在"));
        }
        WorkOrder w = new WorkOrder();
        w.setEquipmentId(req.equipmentId());
        w.setTitle(req.title());
        w.setType(TYPES.contains(req.type()) ? req.type() : "inspection");
        w.setPriority(PRIORITIES.contains(req.priority()) ? req.priority() : "medium");
        w.setDescription(req.description() == null ? "" : req.description());
        w.setAssignee(req.assignee() == null ? "" : req.assignee());
        w.setAssigneeId(req.assigneeId());
        w.setRequiredSkills(req.requiredSkills() == null ? "" : req.requiredSkills());
        w.setEstimatedHours(req.estimatedHours() == null ? 1.0 : req.estimatedHours());
        w.setEquipmentLocation(req.equipmentLocation() != null && !req.equipmentLocation().isBlank()
                ? req.equipmentLocation() : eq.getLocation());
        w.setEquipmentArea(req.equipmentArea() != null ? req.equipmentArea() : eq.getLocation());
        w.setCanGrab(Boolean.TRUE.equals(req.canGrab()));
        w.setStatus("open");
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(w));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody WorkOrderRequest req) {
        WorkOrder w = repo.findById(id).orElse(null);
        if (w == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "工单不存在"));
        }
        if (req.title() != null && !req.title().isBlank()) w.setTitle(req.title());
        if (req.type() != null && TYPES.contains(req.type())) w.setType(req.type());
        if (req.priority() != null && PRIORITIES.contains(req.priority())) w.setPriority(req.priority());
        if (req.description() != null) w.setDescription(req.description());
        if (req.requiredSkills() != null) w.setRequiredSkills(req.requiredSkills());
        if (req.estimatedHours() != null) w.setEstimatedHours(req.estimatedHours());
        if (req.equipmentLocation() != null) w.setEquipmentLocation(req.equipmentLocation());
        if (req.equipmentArea() != null) w.setEquipmentArea(req.equipmentArea());
        if (req.canGrab() != null) w.setCanGrab(req.canGrab());
        return ResponseEntity.ok(repo.save(w));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        WorkOrder w = repo.findById(id).orElse(null);
        if (w == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "工单不存在"));
        }
        if (req.status() == null || !STATUSES.contains(req.status())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "状态不合法"));
        }
        w.setStatus(req.status());
        LocalDateTime now = LocalDateTime.now();
        switch (req.status()) {
            case "accepted" -> w.setAcceptedAt(now);
            case "arrived" -> w.setArrivedAt(now);
            case "in_progress" -> w.setStartedAt(now);
            case "done" -> w.setClosedAt(now);
            case "cancelled" -> w.setClosedAt(now);
            default -> {}
        }
        return ResponseEntity.ok(repo.save(w));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "工单不存在"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
