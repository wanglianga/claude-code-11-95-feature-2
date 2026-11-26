package com.fishport.web;

import com.fishport.service.WorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工作台", description = "各角色从同一服务确认当前责任与下一步操作")
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @Operation(summary = "当前角色的责任清单与下一步操作")
    @GetMapping("/tasks")
    public List<WorkbenchService.TaskItem> tasks(Authentication auth) {
        return workbenchService.tasksFor(VoyageController.roleOf(auth));
    }
}
