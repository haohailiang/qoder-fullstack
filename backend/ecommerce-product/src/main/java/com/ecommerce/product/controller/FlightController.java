package com.ecommerce.product.controller;

import com.ecommerce.common.Result;
import com.ecommerce.product.entity.Flight;
import com.ecommerce.product.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/flights")
@Tag(name = "航班管理", description = "机票航班 CRUD、频率统计等接口")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    /** 查询所有航班 */
    @GetMapping
    @Operation(summary = "查询所有航班", description = "查询全部航班列表（含关联商品信息）")
    public Result<List<Flight>> getAllFlights() {
        return Result.success(flightService.getAllFlights());
    }

    /** 根据 ID 查询航班详情 */
    @GetMapping("/{id}")
    @Operation(summary = "查询航班详情", description = "根据航班 ID 查询详细信息")
    public Result<Flight> getFlightById(@Parameter(description = "航班 ID") @PathVariable Long id) {
        return Result.success(flightService.getFlightById(id));
    }

    /** 按出发城市/落地城市筛选航班 */
    @GetMapping("/search")
    @Operation(summary = "筛选航班", description = "按出发城市和/或落地城市筛选航班")
    public Result<List<Flight>> searchFlights(
            @RequestParam(required = false) @Parameter(description = "出发城市") String departureCity,
            @RequestParam(required = false) @Parameter(description = "落地城市") String arrivalCity) {
        return Result.success(flightService.searchFlights(departureCity, arrivalCity));
    }

    /** 获取城市间航班频率统计（供飞线图使用） */
    @GetMapping("/frequency")
    @Operation(summary = "航班频率统计", description = "获取城市间航班频率统计数据，供 Admin 飞线图使用")
    public Result<Map<String, Object>> getFrequencyData() {
        return Result.success(flightService.getFrequencyData());
    }

    /** 创建航班（管理接口，仅 ADMIN） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "创建航班", description = "管理接口，创建新航班，需关联已有商品 ID")
    public Result<Flight> createFlight(@Valid @RequestBody Flight flight) {
        return Result.success(flightService.createFlight(flight));
    }

    /** 更新航班（管理接口，仅 ADMIN） */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新航班", description = "管理接口，根据航班 ID 更新航班信息")
    public Result<Flight> updateFlight(@Parameter(description = "航班 ID") @PathVariable Long id,
                                        @Valid @RequestBody Flight flight) {
        return Result.success(flightService.updateFlight(id, flight));
    }

    /** 删除航班（管理接口，仅 ADMIN） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除航班", description = "管理接口，根据航班 ID 删除指定航班")
    public Result<Void> deleteFlight(@Parameter(description = "航班 ID") @PathVariable Long id) {
        flightService.deleteFlight(id);
        return Result.success(null);
    }
}
