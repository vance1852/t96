package com.admin.equipment.web;

import com.admin.equipment.service.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/board")
    public ResponseEntity<?> getDispatchBoard(@RequestParam(required = false) Long teamId) {
        return ResponseEntity.ok(statsService.getDispatchBoard(teamId));
    }

    @GetMapping("/staff/{staffId}/workload")
    public ResponseEntity<?> getStaffWorkload(
            @PathVariable Long staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        var wl = statsService.getStaffWorkload(staffId, start, end);
        if (wl == null) {
            return ResponseEntity.status(404).body(Map.of("detail", "人员不存在"));
        }
        return ResponseEntity.ok(wl);
    }

    @GetMapping("/efficiency")
    public ResponseEntity<?> getEfficiencyReport(@RequestParam(defaultValue = "30") int days) {
        if (days <= 0 || days > 365) days = 30;
        return ResponseEntity.ok(statsService.getEfficiencyReport(days));
    }

    @GetMapping("/grabable")
    public ResponseEntity<?> getGrabable() {
        return ResponseEntity.ok(statsService.getGrabableOrders());
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(@RequestParam(required = false) Long workOrderId,
                                     @RequestParam(required = false) Long staffId) {
        return ResponseEntity.ok(statsService.getAssignmentLogs(workOrderId, staffId));
    }
}
