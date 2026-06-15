package com.admin.equipment.web;

import com.admin.equipment.repo.EquipmentRepository;
import com.admin.equipment.repo.StaffRepository;
import com.admin.equipment.repo.TeamRepository;
import com.admin.equipment.repo.WorkOrderRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final EquipmentRepository equipmentRepo;
    private final WorkOrderRepository workOrderRepo;
    private final StaffRepository staffRepo;
    private final TeamRepository teamRepo;

    public DashboardController(EquipmentRepository equipmentRepo, WorkOrderRepository workOrderRepo,
                               StaffRepository staffRepo, TeamRepository teamRepo) {
        this.equipmentRepo = equipmentRepo;
        this.workOrderRepo = workOrderRepo;
        this.staffRepo = staffRepo;
        this.teamRepo = teamRepo;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("equipment_total", equipmentRepo.count());
        m.put("equipment_fault", equipmentRepo.countByStatus("fault"));
        m.put("equipment_maintenance", equipmentRepo.countByStatus("maintenance"));
        m.put("work_order_total", workOrderRepo.count());
        m.put("work_order_open", workOrderRepo.countByStatus("open"));
        m.put("work_order_assigned", workOrderRepo.countByStatus("assigned"));
        m.put("work_order_accepted", workOrderRepo.countByStatus("accepted"));
        m.put("work_order_arrived", workOrderRepo.countByStatus("arrived"));
        m.put("work_order_in_progress", workOrderRepo.countByStatus("in_progress"));
        m.put("work_order_done", workOrderRepo.countByStatus("done"));
        m.put("staff_total", staffRepo.count());
        m.put("staff_active", staffRepo.findByIsActiveTrue().size());
        m.put("team_total", teamRepo.count());
        m.put("team_active", teamRepo.findByIsActiveTrue().size());
        return m;
    }
}
