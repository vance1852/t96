package com.admin.equipment.web;

import com.admin.equipment.model.LeaveRecord;
import com.admin.equipment.repo.LeaveRecordRepository;
import com.admin.equipment.repo.StaffRepository;
import com.admin.equipment.service.DispatchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private static final Set<String> TYPES = Set.of("annual", "personal", "sick", "business", "other");
    private static final Set<String> STATUSES = Set.of("pending", "approved", "rejected", "cancelled");

    private final LeaveRecordRepository repo;
    private final StaffRepository staffRepo;
    private final DispatchService dispatchService;

    public LeaveController(LeaveRecordRepository repo, StaffRepository staffRepo,
                           DispatchService dispatchService) {
        this.repo = repo;
        this.staffRepo = staffRepo;
        this.dispatchService = dispatchService;
    }

    public record LeaveRequest(Long staffId, String type,
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                               String reason) {}

    public record ApproveRequest(Long approverId, String status, boolean triggerReassign) {}

    @GetMapping
    public List<LeaveRecord> list(@RequestParam(required = false) Long staffId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (staffId != null) {
            return repo.findByStaffId(staffId);
        }
        if (status != null && STATUSES.contains(status)) {
            return repo.findByStatus(status);
        }
        if (start != null && end != null) {
            return repo.findApprovedInDateRange(start, end);
        }
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "请假记录不存在")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody LeaveRequest req) {
        if (req.staffId() == null || req.startDate() == null || req.endDate() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "人员、起止日期必填"));
        }
        if (!staffRepo.existsById(req.staffId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "人员不存在"));
        }
        if (req.endDate().isBefore(req.startDate())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "结束日期不能早于开始日期"));
        }
        String type = TYPES.contains(req.type()) ? req.type() : "personal";

        LeaveRecord l = new LeaveRecord();
        l.setStaffId(req.staffId());
        l.setType(type);
        l.setStartDate(req.startDate());
        l.setEndDate(req.endDate());
        l.setReason(req.reason() == null ? "" : req.reason());
        l.setStatus("pending");
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(l));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody ApproveRequest req) {
        LeaveRecord l = repo.findById(id).orElse(null);
        if (l == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "请假记录不存在"));
        }
        String status = req.status() == null ? "approved" : req.status();
        if (!STATUSES.contains(status)) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "状态不合法"));
        }
        l.setStatus(status);
        l.setApprovedBy(req.approverId());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("leave", repo.save(l));

        if ("approved".equals(status) && Boolean.TRUE.equals(req.triggerReassign()) && !l.getReassignCompleted()) {
            var reassignResult = dispatchService.reassignForLeave(
                    l.getStaffId(), l.getStartDate(), l.getEndDate(), req.approverId());
            l.setReassignCompleted(true);
            repo.save(l);
            response.put("reassignResult", reassignResult);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reassign")
    public ResponseEntity<?> triggerReassign(@PathVariable Long id,
                                             @RequestBody(required = false) Map<String, Long> body) {
        LeaveRecord l = repo.findById(id).orElse(null);
        if (l == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "请假记录不存在"));
        }
        if (!"approved".equals(l.getStatus())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "请假未审批，无法重分派"));
        }
        Long operatorId = body != null ? body.get("operatorId") : null;
        var result = dispatchService.reassignForLeave(l.getStaffId(), l.getStartDate(), l.getEndDate(), operatorId);
        l.setReassignCompleted(true);
        repo.save(l);
        return ResponseEntity.ok(Map.of("reassignResult", result, "detail", "重分派完成"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody LeaveRequest req) {
        LeaveRecord l = repo.findById(id).orElse(null);
        if (l == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "请假记录不存在"));
        }
        if (!"pending".equals(l.getStatus())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "只能修改待审批的请假"));
        }
        if (req.type() != null && TYPES.contains(req.type())) l.setType(req.type());
        if (req.startDate() != null) l.setStartDate(req.startDate());
        if (req.endDate() != null) l.setEndDate(req.endDate());
        if (req.reason() != null) l.setReason(req.reason());
        if (l.getEndDate().isBefore(l.getStartDate())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "结束日期不能早于开始日期"));
        }
        return ResponseEntity.ok(repo.save(l));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "请假记录不存在"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
