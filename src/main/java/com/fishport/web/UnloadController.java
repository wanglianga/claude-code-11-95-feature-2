package com.fishport.web;

import com.fishport.domain.UnloadRecord;
import com.fishport.domain.WeighTicket;
import com.fishport.service.IdemResult;
import com.fishport.service.VoyageService;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "卸货与称重", description = "分物种卸货申报、地磅票据（幂等）、签收结算")
@RestController
@RequestMapping("/api")
public class UnloadController {

    private final VoyageService voyageService;

    public UnloadController(VoyageService voyageService) {
        this.voyageService = voyageService;
    }

    @Operation(summary = "申报分物种卸货")
    @PostMapping("/voyages/{voyageNo}/unload-records")
    public UnloadRecord declare(@PathVariable String voyageNo,
                                @Valid @RequestBody Requests.DeclareUnloadRequest req,
                                Authentication auth) {
        return voyageService.declareUnload(voyageNo, req.speciesCode(), req.declaredKg(), auth.getName());
    }

    @Operation(summary = "上传地磅票据（幂等：ticket_no 重传返回原处理结果；争议记录上的新票即复称）")
    @PostMapping("/weigh-tickets")
    public IdemResult<WeighTicket> uploadTicket(@Valid @RequestBody Requests.WeighTicketRequest req) {
        return voyageService.uploadTicket(req.ticketNo(), req.voyageNo(), req.speciesCode(),
                req.grossKg(), req.tareKg(), req.weighedAt(), "API");
    }

    @Operation(summary = "签收实称", description = "按物种扣减额度并释放未捕部分；重复签收返回原结果，不重复扣减")
    @PostMapping("/unload-records/{id}/sign")
    public IdemResult<UnloadRecord> sign(@PathVariable Long id, Authentication auth) {
        return voyageService.sign(id, auth.getName());
    }
}
