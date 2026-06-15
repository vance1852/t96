package com.admin.equipment.web;

import com.admin.equipment.model.Skill;
import com.admin.equipment.model.Staff;
import com.admin.equipment.repo.AppUserRepository;
import com.admin.equipment.repo.SkillRepository;
import com.admin.equipment.repo.StaffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private static final Set<String> LEVELS = Set.of("trainee", "junior", "intermediate", "senior", "expert");

    private final StaffRepository repo;
    private final AppUserRepository userRepo;
    private final SkillRepository skillRepo;

    public StaffController(StaffRepository repo, AppUserRepository userRepo, SkillRepository skillRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.skillRepo = skillRepo;
    }

    public record StaffRequest(Long userId, String staffNo, String displayName, String phone,
                               String level, String area, Double maxDailyHours,
                               Double maxWeeklyHours, Integer maxConsecutiveDays,
                               List<Long> skillIds) {}

    @GetMapping
    public List<Staff> list(@RequestParam(required = false) String area,
                            @RequestParam(required = false) Boolean active,
                            @RequestParam(required = false) Long skillId) {
        if (skillId != null) {
            return repo.findBySkillId(skillId);
        }
        if (area != null && !area.isBlank()) {
            return repo.findByArea(area).stream()
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
                .<ResponseEntity<?>>map(s -> {
                    s.getSkills().size();
                    return ResponseEntity.ok(s);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "人员不存在")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody StaffRequest req) {
        if (req.userId() == null || req.staffNo() == null || req.staffNo().isBlank()
                || req.displayName() == null || req.displayName().isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "用户ID、工号、姓名必填"));
        }
        if (!userRepo.existsById(req.userId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "关联用户不存在"));
        }
        if (repo.existsByStaffNo(req.staffNo())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "工号已存在"));
        }
        if (repo.existsByUserId(req.userId())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "该用户已关联维修人员"));
        }
        Staff s = new Staff();
        s.setUserId(req.userId());
        s.setStaffNo(req.staffNo());
        s.setDisplayName(req.displayName());
        s.setPhone(req.phone() == null ? "" : req.phone());
        s.setLevel(LEVELS.contains(req.level()) ? req.level() : "junior");
        s.setArea(req.area() == null ? "" : req.area());
        s.setMaxDailyHours(req.maxDailyHours() == null ? 8.0 : req.maxDailyHours());
        s.setMaxWeeklyHours(req.maxWeeklyHours() == null ? 40.0 : req.maxWeeklyHours());
        s.setMaxConsecutiveDays(req.maxConsecutiveDays() == null ? 6 : req.maxConsecutiveDays());
        s.setIsActive(true);
        if (req.skillIds() != null && !req.skillIds().isEmpty()) {
            List<Skill> skills = new ArrayList<>();
            for (Long sid : req.skillIds()) {
                skillRepo.findById(sid).ifPresent(skills::add);
            }
            s.setSkills(skills);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(s));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody StaffRequest req) {
        Staff s = repo.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "人员不存在"));
        }
        if (req.displayName() != null && !req.displayName().isBlank()) s.setDisplayName(req.displayName());
        if (req.phone() != null) s.setPhone(req.phone());
        if (req.level() != null && LEVELS.contains(req.level())) s.setLevel(req.level());
        if (req.area() != null) s.setArea(req.area());
        if (req.maxDailyHours() != null) s.setMaxDailyHours(req.maxDailyHours());
        if (req.maxWeeklyHours() != null) s.setMaxWeeklyHours(req.maxWeeklyHours());
        if (req.maxConsecutiveDays() != null) s.setMaxConsecutiveDays(req.maxConsecutiveDays());
        if (req.skillIds() != null) {
            List<Skill> skills = new ArrayList<>();
            for (Long sid : req.skillIds()) {
                skillRepo.findById(sid).ifPresent(skills::add);
            }
            s.setSkills(skills);
        }
        return ResponseEntity.ok(repo.save(s));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Staff s = repo.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "人员不存在"));
        }
        Boolean active = body.get("active");
        if (active != null) s.setIsActive(active);
        return ResponseEntity.ok(repo.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "人员不存在"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
