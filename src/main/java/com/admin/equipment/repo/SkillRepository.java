package com.admin.equipment.repo;

import com.admin.equipment.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findByCode(String code);
    List<Skill> findByEquipmentType(String equipmentType);
    boolean existsByCode(String code);
}
