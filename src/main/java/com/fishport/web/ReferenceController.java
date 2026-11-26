package com.fishport.web;

import com.fishport.repo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "参考数据", description = "海区、物种、船只、港口资源")
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final FishingAreaRepository areaRepo;
    private final SpeciesRepository speciesRepo;
    private final VesselRepository vesselRepo;
    private final PortResourceRepository portRepo;

    public ReferenceController(FishingAreaRepository areaRepo, SpeciesRepository speciesRepo,
                               VesselRepository vesselRepo, PortResourceRepository portRepo) {
        this.areaRepo = areaRepo;
        this.speciesRepo = speciesRepo;
        this.vesselRepo = vesselRepo;
        this.portRepo = portRepo;
    }

    @Operation(summary = "海区列表")
    @GetMapping("/areas")
    public List<?> areas() {
        return areaRepo.findAll();
    }

    @Operation(summary = "物种列表")
    @GetMapping("/species")
    public List<?> species() {
        return speciesRepo.findAll();
    }

    @Operation(summary = "船只列表")
    @GetMapping("/vessels")
    public List<?> vessels() {
        return vesselRepo.findAll();
    }

    @Operation(summary = "港口资源")
    @GetMapping("/ports")
    public List<?> ports() {
        return portRepo.findAll();
    }
}
