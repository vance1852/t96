package com.admin.equipment.web;

import com.admin.equipment.model.Skill;
import com.admin.equipment.repo.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillRepository repo;

    public SkillController(SkillRepository repo) {
        this.repo = repo;
    }

    public record SkillRequest(String code, String name, String description, String equipmentType) {}

    @GetMapping
    public List<Skill> list(@RequestParam(required = false) String equipmentType) {
        if (equipmentType != null && !equipmentType.isBlank()) {
            return repo.findByEquipmentType(equipmentType);
        }
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "技能不存在")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SkillRequest req) {
        if (req.code() == null || req.code().isBlank() || req.name() == null || req.name().isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "编码和名称必填"));
        }
        if (repo.existsByCode(req.code())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "技能编码已存在"));
        }
        Skill s = new Skill();
        s.setCode(req.code());
        s.setName(req.name());
        s.setDescription(req.description() == null ? "" : req.description());
        s.setEquipmentType(req.equipmentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(s));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SkillRequest req) {
        Skill s = repo.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "技能不存在"));
        }
        if (req.name() != null && !req.name().isBlank()) s.setName(req.name());
        if (req.description() != null) s.setDescription(req.description());
        if (req.equipmentType() != null) s.setEquipmentType(req.equipmentType());
        return ResponseEntity.ok(repo.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "技能不存在"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
