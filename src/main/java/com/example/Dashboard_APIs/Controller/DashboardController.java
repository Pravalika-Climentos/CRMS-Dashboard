package com.example.Dashboard_APIs.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Dashboard_APIs.DTO.DashboardData;
import com.example.Dashboard_APIs.Service.DashboardService;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

     public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

   @GetMapping("/dashboard")
    public ResponseEntity<DashboardData> getDashboardData() {
        DashboardData dashboardData = dashboardService.getDashboardData();

        return ResponseEntity.ok(dashboardData);
    }
}