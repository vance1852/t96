package com.admin.equipment.web;

import com.admin.equipment.model.ShiftSchedule;
import com.admin.equipment.model.Staff;
import com.admin.equipment.repo.ShiftRepository;
import com.admin.equipment.repo.ShiftScheduleRepository;
import com.admin.equipment.repo.StaffRepository;
import com.admin.equipment.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ShiftScheduleRepository scheduleRepo;
    private final StaffRepository staffRepo;
    private final ShiftRepository shiftRepo;

    public ScheduleController(ScheduleService scheduleService, ShiftScheduleRepository scheduleRepo,
                              StaffRepository staffRepo, ShiftRepository shiftRepo) {
        this.scheduleService = scheduleService;
        this.scheduleRepo = scheduleRepo;
        this.staffRepo = staffRepo;
        this.shiftRepo = shiftRepo;
    }

    public record ScheduleItemRequest(Long staffId, LocalDate scheduleDate, Long shiftId,
                                      String shiftCode, String shiftType, String remark) {}

    public record BatchScheduleRequest(List<ScheduleItemRequest> items, boolean forceSave) {}

    public record GenerateRequest(Long teamId,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {}

    @GetMapping
    public List<ShiftSchedule> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long staffId) {
        if (staffId != null) {
            return scheduleRepo.findByStaffIdAndScheduleDateBetween(staffId, startDate, endDate);
        }
        return scheduleRepo.findByScheduleDateBetween(startDate, endDate);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return scheduleRepo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "排班记录不存在")));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody BatchScheduleRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "排班项不能为空"));
        }
        List<ShiftSchedule> schedules = convertToSchedules(req.items());
        LocalDate start = schedules.stream().map(ShiftSchedule::getScheduleDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = schedules.stream().map(ShiftSchedule::getScheduleDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        return ResponseEntity.ok(scheduleService.validateSchedule(schedules, start, end));
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody BatchScheduleRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "排班项不能为空"));
        }
        List<ShiftSchedule> schedules = convertToSchedules(req.items());
        LocalDate start = schedules.stream().map(ShiftSchedule::getScheduleDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = schedules.stream().map(ShiftSchedule::getScheduleDate).max(LocalDate::compareTo).orElse(LocalDate.now());

        ScheduleService.ScheduleValidationResult validation = scheduleService.validateSchedule(schedules, start, end);
        if (!validation.valid() && !req.forceSave()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "detail", "排班存在冲突，请检查或使用forceSave=true强制保存",
                    "errors", validation.errors(),
                    "coverage", validation.coverage()
            ));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.saveSchedules(schedules));
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest req) {
        if (req.teamId() == null || req.startDate() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "班组ID和起始日期必填"));
        }
        List<ShiftSchedule> schedules = scheduleService.generateWeeklyAutoSchedule(req.teamId(), req.startDate());
        if (schedules.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "班组不存在或无成员/班次"));
        }
        LocalDate end = req.startDate().plusDays(6);
        ScheduleService.ScheduleValidationResult validation = scheduleService.validateSchedule(schedules, req.startDate(), end);
        return ResponseEntity.ok(Map.of(
                "schedules", schedules,
                "validation", validation
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ScheduleItemRequest req) {
        ShiftSchedule s = scheduleRepo.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "排班记录不存在"));
        }
        if (req.shiftId() != null) s.setShiftId(req.shiftId());
        if (req.shiftCode() != null) s.setShiftCode(req.shiftCode());
        if (req.shiftType() != null) s.setShiftType(req.shiftType());
        if (req.remark() != null) s.setRemark(req.remark());
        return ResponseEntity.ok(scheduleRepo.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!scheduleRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "排班记录不存在"));
        }
        scheduleRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/on-duty")
    public ResponseEntity<?> getOnDuty(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String shiftType) {
        Map<Long, Staff> onDuty = scheduleService.getOnDutyStaffAt(date, shiftType);
        return ResponseEntity.ok(onDuty.values());
    }

    private List<ShiftSchedule> convertToSchedules(List<ScheduleItemRequest> items) {
        List<ShiftSchedule> list = new ArrayList<>();
        for (ScheduleItemRequest item : items) {
            ShiftSchedule s = new ShiftSchedule();
            s.setStaffId(item.staffId());
            s.setScheduleDate(item.scheduleDate());
            s.setShiftId(item.shiftId());
            s.setShiftCode(item.shiftCode());
            s.setShiftType(item.shiftType());
            s.setRemark(item.remark());
            if (s.getShiftType() == null && item.shiftId() != null) {
                shiftRepo.findById(item.shiftId()).ifPresent(sh -> s.setShiftType(sh.getShiftType()));
            }
            list.add(s);
        }
        return list;
    }
}
