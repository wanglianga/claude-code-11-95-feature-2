package com.fishport.web;

import com.fishport.domain.*;
import com.fishport.service.*;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "航次", description = "离港、返港、分物种卸货、复称、结案；到港与结案幂等")
@RestController
@RequestMapping("/api/voyages")
public class VoyageController {

    private final VoyageService voyageService;
    private final NoteService noteService;

    public VoyageController(VoyageService voyageService, NoteService noteService) {
        this.voyageService = voyageService;
        this.noteService = noteService;
    }

    @Operation(summary = "离港（幂等：重复离港返回原处理结果）")
    @PostMapping("/{voyageNo}/depart")
    public IdemResult<Voyage> depart(@PathVariable String voyageNo, Authentication auth) {
        return voyageService.depart(voyageNo, auth.getName());
    }

    @Operation(summary = "返港到港（幂等：重复到港返回原处理结果）")
    @PostMapping("/{voyageNo}/arrive")
    public IdemResult<Voyage> arrive(@PathVariable String voyageNo, Authentication auth) {
        return voyageService.arrive(voyageNo, auth.getName());
    }

    @Operation(summary = "结案", description = "全部物种签收且无未办结争议方可结案；许可证过期自动登记争议并阻断")
    @PostMapping("/{voyageNo}/close")
    public IdemResult<Voyage> close(@PathVariable String voyageNo, Authentication auth) {
        return voyageService.close(voyageNo, auth.getName());
    }

    @Operation(summary = "航次详情")
    @GetMapping("/{voyageNo}")
    public Voyage get(@PathVariable String voyageNo) {
        return voyageService.getVoyage(voyageNo);
    }

    @Operation(summary = "航次卸货记录")
    @GetMapping("/{voyageNo}/records")
    public List<UnloadRecord> records(@PathVariable String voyageNo) {
        return voyageService.recordsOf(voyageNo);
    }

    @Operation(summary = "航次争议列表")
    @GetMapping("/{voyageNo}/disputes")
    public List<Dispute> disputes(@PathVariable String voyageNo) {
        return voyageService.disputesOf(voyageNo);
    }

    @Operation(summary = "登记争议", description = "重量争议 / 混装来源不清 / 许可证过期；未办结前阻断相关物种结案")
    @PostMapping("/{voyageNo}/disputes")
    public Dispute raiseDispute(@PathVariable String voyageNo,
                                @Valid @RequestBody Requests.DisputeRequest req,
                                Authentication auth) {
        return voyageService.raiseDispute(voyageNo, req.unloadRecordId(),
                Types.DisputeType.valueOf(req.type()), req.description(), auth.getName());
    }

    @Operation(summary = "添加备注", description = "仅执法人员(ENFORCEMENT)的备注记为执法事实；客服备注不算")
    @PostMapping("/{voyageNo}/notes")
    public CaseNote addNote(@PathVariable String voyageNo, @Valid @RequestBody Requests.NoteRequest req,
                            Authentication auth) {
        return noteService.addNote(voyageNo, req.content(), auth.getName(), roleOf(auth));
    }

    @Operation(summary = "航次备注列表")
    @GetMapping("/{voyageNo}/notes")
    public List<CaseNote> notes(@PathVariable String voyageNo) {
        return noteService.notesOf(voyageNo);
    }

    static String roleOf(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5))
                .filter(r -> !r.equals("ADMIN")).findFirst().orElse("ADMIN");
    }
}
