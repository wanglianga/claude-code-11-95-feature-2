package com.fishport.web;

import com.fishport.domain.Dispute;
import com.fishport.domain.Types;
import com.fishport.repo.DisputeRepository;
import com.fishport.service.VoyageService;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "争议", description = "争议查询与办结；办结仅执法人员，办结意见记为执法事实")
@RestController
@RequestMapping("/api/disputes")
public class DisputeController {

    private final VoyageService voyageService;
    private final DisputeRepository disputeRepo;

    public DisputeController(VoyageService voyageService, DisputeRepository disputeRepo) {
        this.voyageService = voyageService;
        this.disputeRepo = disputeRepo;
    }

    @Operation(summary = "办结争议（仅执法人员）")
    @PostMapping("/{id}/resolve")
    public Dispute resolve(@PathVariable Long id, @Valid @RequestBody Requests.ResolveRequest req,
                           Authentication auth) {
        return voyageService.resolveDispute(id, req.resolution(), auth.getName());
    }

    @Operation(summary = "争议列表")
    @GetMapping
    public List<Dispute> list(@RequestParam(defaultValue = "OPEN") String status) {
        return disputeRepo.findByStatus(Types.DisputeStatus.valueOf(status));
    }
}
