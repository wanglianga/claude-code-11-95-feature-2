package com.fishport.web;

import com.fishport.domain.Appointment;
import com.fishport.domain.Notification;
import com.fishport.domain.Types;
import com.fishport.service.ScheduleService;
import com.fishport.web.dto.Requests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "卸港预约", description = "固定时段 / 滚动叫号双模式调度；清场占用、迟到改期、重排通知")
@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final ScheduleService scheduleService;

    public AppointmentController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "预约登记", description = "FIXED 需给申请时段起点 requestedStart；ROLLING 需给计划到港 plannedArrival；校验冷库容量")
    @PostMapping("/appointments")
    public Appointment book(@Valid @RequestBody Requests.BookAppointmentRequest req) {
        return scheduleService.book(req.voyageNo(), req.portCode(), Types.ApptMode.valueOf(req.mode()),
                req.requestedStart(), req.plannedArrival(), req.durationMin(), req.latestFinish(),
                req.expectedUnloadKg());
    }

    @Operation(summary = "计算并公布排队", description = "FIXED 按已通知时段分配；ROLLING 按实际/计划到港先后滚动排序")
    @PostMapping("/appointments/compute")
    public List<Appointment> compute(@Valid @RequestBody Requests.ComputeRequest req) {
        return scheduleService.compute(req.portCode(), req.date(), Types.ApptMode.valueOf(req.mode()));
    }

    @Operation(summary = "到港打卡", description = "记录实际到港并重排：固定模式迟到占住空档、无法按期则改期队尾；滚动模式改变已公布顺序并重新通知")
    @PostMapping("/appointments/{appointmentNo}/checkin")
    public Appointment checkin(@PathVariable String appointmentNo,
                               @Valid @RequestBody Requests.CheckinRequest req) {
        return scheduleService.checkin(appointmentNo, req.actualArrival());
    }

    @Operation(summary = "卸货完成，释放卸货线")
    @PostMapping("/appointments/{appointmentNo}/finish")
    public Appointment finish(@PathVariable String appointmentNo) {
        return scheduleService.finish(appointmentNo);
    }

    @Operation(summary = "港口当日排队")
    @GetMapping("/appointments")
    public List<Appointment> list(@RequestParam String portCode,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleService.listByPortAndDate(portCode, date);
    }

    @Operation(summary = "航次通知记录（排定/改期/队列变化）")
    @GetMapping("/voyages/{voyageId}/notifications")
    public List<Notification> notifications(@PathVariable Long voyageId) {
        return scheduleService.notificationsOf(voyageId);
    }
}
