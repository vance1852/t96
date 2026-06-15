package com.admin.equipment.web;

import com.admin.equipment.service.DispatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public record BatchRequest(List<Long> workOrderIds, Long operatorId) {}
    public record AssignRequest(Long staffId, Long operatorId, String reason) {}
    public record GrabRequest(Long staffId) {}
    public record ReturnRequest(Long staffId, String reason) {}
    public record TimingRequest(String action) {}

    @PostMapping("/single/{workOrderId}")
    public ResponseEntity<?> dispatchSingle(@PathVariable Long workOrderId,
                                            @RequestBody(required = false) Map<String, Long> body) {
        Long operatorId = body != null ? body.get("operatorId") : null;
        var result = dispatchService.dispatchSingle(workOrderId, operatorId);
        if (!result.success()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", result.reason(), "result", result));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<?> dispatchBatch(@RequestBody BatchRequest req) {
        if (req.workOrderIds() == null || req.workOrderIds().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "工单ID列表不能为空"));
        }
        return ResponseEntity.ok(dispatchService.dispatchBatch(req.workOrderIds(), req.operatorId()));
    }

    @PostMapping("/all-open")
    public ResponseEntity<?> dispatchAllOpen(@RequestBody(required = false) Map<String, Long> body) {
        Long operatorId = body != null ? body.get("operatorId") : null;
        return ResponseEntity.ok(dispatchService.dispatchAllOpen(operatorId));
    }

    @PostMapping("/compare")
    public ResponseEntity<?> compareAlgorithms(@RequestBody BatchRequest req) {
        if (req.workOrderIds() == null || req.workOrderIds().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "工单ID列表不能为空"));
        }
        return ResponseEntity.ok(dispatchService.compareAlgorithms(req.workOrderIds()));
    }

    @PostMapping("/{workOrderId}/assign")
    public ResponseEntity<?> manualAssign(@PathVariable Long workOrderId, @RequestBody AssignRequest req) {
        if (req.staffId() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "人员ID必填"));
        }
        var result = dispatchService.manualAssign(workOrderId, req.staffId(), req.operatorId(), req.reason());
        if (!result.success()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", result.reason()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{workOrderId}/grab")
    public ResponseEntity<?> grabOrder(@PathVariable Long workOrderId, @RequestBody GrabRequest req) {
        if (req.staffId() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "人员ID必填"));
        }
        var result = dispatchService.grabOrder(workOrderId, req.staffId());
        if (!result.success()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", result.reason()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{workOrderId}/return")
    public ResponseEntity<?> returnOrder(@PathVariable Long workOrderId, @RequestBody ReturnRequest req) {
        if (req.staffId() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "人员ID必填"));
        }
        var result = dispatchService.returnOrder(workOrderId, req.staffId(), req.reason());
        if (!result.success()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", result.reason()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reassign/leave/{staffId}")
    public ResponseEntity<?> reassignForLeave(@PathVariable Long staffId,
                                              @RequestBody Map<String, Object> body) {
        try {
            java.time.LocalDate start = java.time.LocalDate.parse(body.get("startDate").toString());
            java.time.LocalDate end = java.time.LocalDate.parse(body.get("endDate").toString());
            Long operatorId = body.get("operatorId") != null ? Long.valueOf(body.get("operatorId").toString()) : null;
            return ResponseEntity.ok(dispatchService.reassignForLeave(staffId, start, end, operatorId));
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "请求格式错误: " + e.getMessage()));
        }
    }

    @PostMapping("/{workOrderId}/timing")
    public ResponseEntity<?> updateTiming(@PathVariable Long workOrderId, @RequestBody TimingRequest req) {
        if (req.action() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "操作类型必填"));
        }
        try {
            dispatchService.updateWorkOrderTiming(workOrderId, req.action());
            return ResponseEntity.ok(Map.of("detail", "时间打点成功", "action", req.action()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", e.getMessage()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "工单不存在"));
        }
    }
}
