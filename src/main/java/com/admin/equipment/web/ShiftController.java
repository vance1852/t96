package com.admin.equipment.web;

import com.admin.equipment.model.Shift;
import com.admin.equipment.repo.ShiftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private static final Set<String> SHIFT_TYPES = Set.of("morning", "middle", "night", "rest");

    private final ShiftRepository repo;

    public ShiftController(ShiftRepository repo) {
        this.repo = repo;
    }

    public record ShiftRequest(String code, String name, String shiftType,
                               LocalTime startTime, LocalTime endTime, Double workHours) {}

    @GetMapping
    public List<Shift> list(@RequestParam(required = false) String shiftType,
                            @RequestParam(required = false) Boolean active) {
        if (shiftType != null && !shiftType.isBlank()) {
            return repo.findByShiftType(shiftType).stream()
                    .filter(s -> active == null || s.getIsActive().equals(active))
                    .toList();
        }
        if (active != null && !active) {
            return repo.findAll().stream().filter(s -> !s.getIsActive()).toList();
        }
        return repo.findByIsActiveTrue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班次不存在")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ShiftRequest req) {
        if (req.code() == null || req.code().isBlank() || req.name() == null || req.name().isBlank()
                || req.shiftType() == null || !SHIFT_TYPES.contains(req.shiftType())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "编码、名称、班次类型必填且类型合法"));
        }
        if (repo.existsByCode(req.code())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "班次编码已存在"));
        }
        Shift s = new Shift();
        s.setCode(req.code());
        s.setName(req.name());
        s.setShiftType(req.shiftType());
        if ("rest".equals(req.shiftType())) {
            s.setStartTime(LocalTime.of(0, 0));
            s.setEndTime(LocalTime.of(0, 0));
            s.setWorkHours(0.0);
        } else {
            if (req.startTime() == null || req.endTime() == null) {
                return ResponseEntity.unprocessableEntity().body(Map.of("detail", "非休息班次必须设置起止时间"));
            }
            s.setStartTime(req.startTime());
            s.setEndTime(req.endTime());
            s.setWorkHours(req.workHours() == null ? 8.0 : req.workHours());
        }
        s.setIsActive(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(s));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ShiftRequest req) {
        Shift s = repo.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班次不存在"));
        }
        if (req.name() != null && !req.name().isBlank()) s.setName(req.name());
        if (req.shiftType() != null && SHIFT_TYPES.contains(req.shiftType())) s.setShiftType(req.shiftType());
        if (req.startTime() != null) s.setStartTime(req.startTime());
        if (req.endTime() != null) s.setEndTime(req.endTime());
        if (req.workHours() != null) s.setWorkHours(req.workHours());
        return ResponseEntity.ok(repo.save(s));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Shift s = repo.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班次不存在"));
        }
        Boolean active = body.get("active");
        if (active != null) s.setIsActive(active);
        return ResponseEntity.ok(repo.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班次不存在"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
