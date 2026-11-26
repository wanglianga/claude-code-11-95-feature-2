package com.fishport.web;

import com.fishport.domain.RegulatoryReceipt;
import com.fishport.domain.Voyage;
import com.fishport.domain.WeighTicket;
import com.fishport.service.BizException;
import com.fishport.service.IdemResult;
import com.fishport.service.RegulatorService;
import com.fishport.service.VoyageService;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟端：船舶终端、港口地磅、监管回执。
 * 模拟外部设备/系统推送，免登录；重传只返回原处理结果。
 */
@Tag(name = "模拟端", description = "模拟船舶终端 / 港口地磅 / 监管回执（免登录，重传幂等）")
@RestController
@RequestMapping("/api/sim")
public class SimController {

    private final VoyageService voyageService;
    private final RegulatorService regulatorService;

    public SimController(VoyageService voyageService, RegulatorService regulatorService) {
        this.voyageService = voyageService;
        this.regulatorService = regulatorService;
    }

    @Operation(summary = "模拟船舶终端事件", description = "eventType=DEPART/ARRIVE；同一航次重复上报只返回原处理结果")
    @PostMapping("/vessel-terminal/events")
    public IdemResult<Voyage> terminalEvent(@Valid @RequestBody Requests.TerminalEventRequest req) {
        return switch (req.eventType()) {
            case "DEPART" -> voyageService.depart(req.voyageNo(), "SIM-TERMINAL");
            case "ARRIVE" -> voyageService.arrive(req.voyageNo(), "SIM-TERMINAL");
            default -> throw BizException.badRequest("未知终端事件类型: " + req.eventType());
        };
    }

    @Operation(summary = "模拟港口地磅上传票据", description = "ticket_no 唯一，重传返回原票据；争议记录上的新票即复称")
    @PostMapping("/weighbridge/tickets")
    public IdemResult<WeighTicket> weighbridge(@Valid @RequestBody Requests.WeighTicketRequest req) {
        return voyageService.uploadTicket(req.ticketNo(), req.voyageNo(), req.speciesCode(),
                req.grossKg(), req.tareKg(), req.weighedAt(), "SIM-WEIGHBRIDGE");
    }

    @Operation(summary = "监管回执列表（模拟监管端查看已上报事件）")
    @GetMapping("/regulator/receipts")
    public List<RegulatoryReceipt> receipts() {
        return regulatorService.listAll();
    }

    @Operation(summary = "模拟监管端确认回执（幂等）")
    @PostMapping("/regulator/receipts/{receiptNo}/ack")
    public RegulatoryReceipt ack(@PathVariable String receiptNo) {
        return regulatorService.ack(receiptNo);
    }
}
