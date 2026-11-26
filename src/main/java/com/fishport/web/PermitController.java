package com.fishport.web;

import com.fishport.domain.Permit;
import com.fishport.domain.PermitApplication;
import com.fishport.domain.PermitApplicationItem;
import com.fishport.repo.VesselRepository;
import com.fishport.service.BizException;
import com.fishport.service.PermitService;
import com.fishport.service.ReplayService;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "许可", description = "许可申请、五项校验（有效期/海区/季节/渔具/物种额度）与历史回放")
@RestController
@RequestMapping("/api")
public class PermitController {

    private final PermitService permitService;
    private final ReplayService replayService;
    private final VesselRepository vesselRepo;

    public PermitController(PermitService permitService, ReplayService replayService,
                            VesselRepository vesselRepo) {
        this.permitService = permitService;
        this.replayService = replayService;
        this.vesselRepo = vesselRepo;
    }

    @Operation(summary = "提交许可申请并同步校验",
            description = "校验有效期、海区、季节、渔具限制、物种额度（不跨海区/物种合并）；"
                    + "同一船存在未结航次时不得获批。全部通过则发证、建航次并保留额度。")
    @PostMapping("/permit-applications")
    public PermitService.ApplyResult apply(@Valid @RequestBody Requests.ApplyPermitRequest req,
                                           Authentication auth) {
        Long vesselId = vesselRepo.findByVesselNo(req.vesselNo())
                .orElseThrow(() -> BizException.notFound("船只不存在: " + req.vesselNo()))
                .getId();
        PermitApplication app = new PermitApplication();
        app.setVesselId(vesselId);
        app.setCaptainName(req.captainName());
        app.setCrewCount(req.crewCount());
        app.setCrewList(req.crewList());
        app.setAreaCode(req.areaCode());
        app.setGearType(req.gearType());
        app.setPlannedStart(req.plannedStart());
        app.setPlannedEnd(req.plannedEnd());
        for (Requests.ApplyPermitRequest.Item item : req.items()) {
            app.getItems().add(new PermitApplicationItem(item.speciesCode(), item.declaredLimitKg()));
        }
        return permitService.apply(app, auth.getName());
    }

    @Operation(summary = "查询申请详情（含驳回原因）")
    @GetMapping("/permit-applications/{applicationNo}")
    public PermitApplication getApplication(@PathVariable String applicationNo) {
        return permitService.getApplication(applicationNo);
    }

    @Operation(summary = "查询许可详情")
    @GetMapping("/permits/{permitNo}")
    public Permit getPermit(@PathVariable String permitNo) {
        return permitService.getPermit(permitNo);
    }

    @Operation(summary = "历史许可回放", description = "按时间轴重放申请、批复、离港、返港、称重、签收、结案事件与额度流水，并校验流水自洽")
    @GetMapping("/permits/{permitNo}/replay")
    public ReplayService.ReplayResult replay(@PathVariable String permitNo) {
        return replayService.replay(permitNo);
    }
}
