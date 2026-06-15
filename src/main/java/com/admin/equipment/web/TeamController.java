package com.admin.equipment.web;

import com.admin.equipment.model.Staff;
import com.admin.equipment.model.Team;
import com.admin.equipment.repo.StaffRepository;
import com.admin.equipment.repo.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository repo;
    private final StaffRepository staffRepo;

    public TeamController(TeamRepository repo, StaffRepository staffRepo) {
        this.repo = repo;
        this.staffRepo = staffRepo;
    }

    public record TeamRequest(String code, String name, String description,
                              Long leaderId, List<Long> memberIds) {}

    @GetMapping
    public List<Team> list(@RequestParam(required = false) Boolean active) {
        if (active != null && !active) {
            return repo.findAll().stream().filter(t -> !t.getIsActive()).toList();
        }
        return repo.findByIsActiveTrue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(t -> {
                    t.getMembers().size();
                    return ResponseEntity.ok(t);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班组不存在")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TeamRequest req) {
        if (req.code() == null || req.code().isBlank() || req.name() == null || req.name().isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "编码和名称必填"));
        }
        if (repo.existsByCode(req.code())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "班组编码已存在"));
        }
        Team t = new Team();
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description() == null ? "" : req.description());
        t.setLeaderId(req.leaderId());
        t.setIsActive(true);
        if (req.memberIds() != null && !req.memberIds().isEmpty()) {
            List<Staff> members = new ArrayList<>();
            for (Long mid : req.memberIds()) {
                staffRepo.findById(mid).ifPresent(members::add);
            }
            t.setMembers(members);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(t));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TeamRequest req) {
        Team t = repo.findById(id).orElse(null);
        if (t == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班组不存在"));
        }
        if (req.name() != null && !req.name().isBlank()) t.setName(req.name());
        if (req.description() != null) t.setDescription(req.description());
        if (req.leaderId() != null) t.setLeaderId(req.leaderId());
        if (req.memberIds() != null) {
            List<Staff> members = new ArrayList<>();
            for (Long mid : req.memberIds()) {
                staffRepo.findById(mid).ifPresent(members::add);
            }
            t.setMembers(members);
        }
        return ResponseEntity.ok(repo.save(t));
    }

    @PatchMapping("/{id}/members")
    public ResponseEntity<?> updateMembers(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        Team t = repo.findById(id).orElse(null);
        if (t == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班组不存在"));
        }
        List<Long> memberIds = body.get("memberIds");
        if (memberIds != null) {
            List<Staff> members = new ArrayList<>();
            for (Long mid : memberIds) {
                staffRepo.findById(mid).ifPresent(members::add);
            }
            t.setMembers(members);
        }
        return ResponseEntity.ok(repo.save(t));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Team t = repo.findById(id).orElse(null);
        if (t == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班组不存在"));
        }
        Boolean active = body.get("active");
        if (active != null) t.setIsActive(active);
        return ResponseEntity.ok(repo.save(t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "班组不存在"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
