package com.fishport.web;

import com.fishport.domain.*;
import com.fishport.service.CrewRosterService;
import com.fishport.service.SimClock;
import com.fishport.repo.CrewPersonRepository;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * N-5 船员名册替换：投影确认（单一实现，无模型切换）、闸口扫码、离港窗、船东决定留痕。
 */
@Tag(name = "船员名册(N-5)", description = "名册修订版本链、字段投影确认失效/沿用、闸口二维码、离港窗与待放行责任")
@RestController
@RequestMapping("/api/crew")
public class CrewRosterController {

    private final CrewRosterService rosterService;
    private final SimClock clock;
    private final CrewPersonRepository personRepo;

    public CrewRosterController(CrewRosterService rosterService, SimClock clock, CrewPersonRepository personRepo) {
        this.rosterService = rosterService;
        this.clock = clock;
        this.personRepo = personRepo;
    }

    @Operation(summary = "建立初版名册（许可批复后）", description = "按人员主档快照建版，生成三方投影待签确认")
    @PostMapping("/rosters")
    public CrewRosterRevision init(@Valid @RequestBody Requests.InitRosterRequest req, Authentication auth) {
        return rosterService.initializeRoster(req.voyageNo(), req.personNos(), req.requiredQualifications(),
                req.reason(), auth.getName());
    }

    @Operation(summary = "选择一：原船长休息达标后继续", description = "船员关系不变，重签后逐投影核对，未变化确认沿用")
    @PostMapping("/voyages/{voyageNo}/keep-captain-rest")
    public CrewRosterService.ReSignResult keepRest(@PathVariable String voyageNo,
                                                   @RequestBody(required = false) Requests.RosterDecisionRequest req,
                                                   Authentication auth) {
        return rosterService.keepCaptainRest(voyageNo, req == null ? null : req.reason(), auth.getName());
    }

    @Operation(summary = "选择二：启用替补", description = "核岗位、船型权限、证书、休息、航次职责；通过即重签并撤销旧二维码")
    @PostMapping("/voyages/{voyageNo}/substitute")
    public CrewRosterService.ReSignResult substitute(@PathVariable String voyageNo,
                                                     @Valid @RequestBody Requests.SubstituteRequest req,
                                                     Authentication auth) {
        return rosterService.useSubstitute(voyageNo, req.replacementPersonNo(), req.reason(), auth.getName());
    }

    @Operation(summary = "名册更正", description = "可换人（轮机员请假）或仅改应急电话；按字段投影决定失效范围")
    @PostMapping("/voyages/{voyageNo}/corrections")
    public CrewRosterService.ReSignResult correct(@PathVariable String voyageNo,
                                                  @Valid @RequestBody Requests.RosterCorrectionRequest req,
                                                  Authentication auth) {
        return rosterService.correctRoster(voyageNo, req.personNos(), req.emergencyPhones(),
                req.reason(), auth.getName());
    }

    @Operation(summary = "接收方投影确认", description = "party=PORT/INSURER/EMERGENCY；三方齐备即放行并签发新二维码")
    @PostMapping("/revisions/{revisionNo}/ack")
    public RosterAcknowledgement ack(@PathVariable String revisionNo, @RequestParam String party,
                                     Authentication auth) {
        return rosterService.confirmRevision(revisionNo, Types.RosterParty.valueOf(party), auth.getName());
    }

    @Operation(summary = "登记离港窗", description = "kind=CURRENT 当前港 / ALTERNATIVE 另一港；窗口关闭后不再显示可用")
    @PostMapping("/departure-windows")
    public DepartureWindow registerWindow(@Valid @RequestBody Requests.DepartureWindowRequest req) {
        return rosterService.registerWindow(req.voyageNo(), req.portCode(), req.kind(),
                req.windowFrom(), req.windowTo());
    }

    @Operation(summary = "港口闸口扫码", description = "只接受当时有效船员版本；旧二维码拒绝；离港后迟到签到仅留核查不回滚")
    @PostMapping("/gate/scan")
    public CrewRosterService.GateScanResult scan(@Valid @RequestBody Requests.GateScanRequest req) {
        return rosterService.scan(req.qrCode());
    }

    @Operation(summary = "名册当前状态（含投影确认、待放行责任方、窗口可用性、岗位覆盖问题）")
    @GetMapping("/voyages/{voyageNo}/roster")
    public CrewRosterService.RosterStatusView status(@PathVariable String voyageNo) {
        return rosterService.statusOf(voyageNo);
    }

    @Operation(summary = "名册修订历史（版本链）")
    @GetMapping("/voyages/{voyageNo}/revisions")
    public List<CrewRosterRevision> revisions(@PathVariable String voyageNo) {
        return rosterService.historyOf(voyageNo);
    }

    @Operation(summary = "船东决定与通知回执")
    @GetMapping("/voyages/{voyageNo}/decisions")
    public List<OwnerDecision> decisions(@PathVariable String voyageNo) {
        return rosterService.decisionsOf(voyageNo);
    }

    @Operation(summary = "闸口扫码留痕（含离港后迟到核查记录）")
    @GetMapping("/voyages/{voyageNo}/scans")
    public List<GateScanLog> scans(@PathVariable String voyageNo) {
        return rosterService.scansOf(voyageNo);
    }

    @Operation(summary = "某修订的三方投影确认（回执依赖、哈希、失效原因）")
    @GetMapping("/revisions/{revisionNo}/acks")
    public List<CrewRosterService.AckView> acks(@PathVariable String revisionNo) {
        return rosterService.acksOfRevision(revisionNo);
    }

    @Operation(summary = "人员主档（证书/船型权限/资质/休息达标时刻）")
    @GetMapping("/persons")
    public List<CrewPerson> persons() {
        return personRepo.findAll();
    }

    @Operation(summary = "人员请假/复职（模拟）", description = "唯一资质持有人请假后，重签按整名册核验会报资质覆盖失败")
    @PostMapping("/persons/{personNo}/active")
    public CrewPerson setActive(@PathVariable String personNo, @Valid @RequestBody Requests.PersonLeaveRequest req) {
        return rosterService.setPersonActive(personNo, req.active());
    }

    @Operation(summary = "固定模拟时钟（复现 09:00/09:30/11:00 场景，免登录模拟端）")
    @PostMapping("/sim/clock")
    public Map<String, Object> setClock(@Valid @RequestBody Requests.ClockRequest req) {
        clock.setAt(req.at());
        return Map.of("fixedAt", req.at().toString());
    }

    @Operation(summary = "恢复系统时钟")
    @PostMapping("/sim/clock/reset")
    public Map<String, Object> resetClock() {
        clock.reset();
        return Map.of("fixedAt", "system");
    }
}
