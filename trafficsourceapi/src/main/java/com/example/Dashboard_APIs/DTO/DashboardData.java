package com.example.Dashboard_APIs.DTO;

import java.util.List;
import java.util.Map;

public record DashboardData(

        TrafficSources trafficSources,

        SalesPerformance salesPerformance,

        RevenueOverview revenueOverview,

        DashboardCards dashboardCards,

        int monthlyTarget,

        List<Integer> sales,

        PipelineStatistics pipelineStatistics,

        DealsOverview dealsOverview,

        ProfitEarned profitEarned,

        List<SalesExecutive> salesExecutives,

        List<RecentDeal> recentDeals,

        List<DealWonCompany> dealWonCompanies

) {

    // =========================================================
    // TRAFFIC SOURCES
    // =========================================================

    public record TrafficSources(
            List<String> labels,
            List<Integer> series
    ) {}


    // =========================================================
    // SALES PERFORMANCE
    // =========================================================

    public record SalesPerformance(
            List<String> labels,
            List<Integer> revenue,
            int target
    ) {}


    // =========================================================
    // REVENUE OVERVIEW
    // =========================================================

    public record RevenueOverview(
            List<Double> monthly,
            Double currentMonthRevenue
    ) {}


    // =========================================================
    // DASHBOARD SUMMARY CARDS
    // =========================================================

    public record DashboardCards(
            long totalSales,
            int totalOrders,
            int newCustomers,
            double conversionRate,
            int activeDeals,
            int totalContacts
    ) {}


    // =========================================================
    // PIPELINE STATISTICS
    // =========================================================

    public record PipelineStatistics(
            List<PipelineStage> stages
    ) {}


    public record PipelineStage(
            String name,
            int value,
            int deals
    ) {}


    // =========================================================
    // DEALS OVERVIEW
    // =========================================================

    public record DealsOverview(
            int totalDeals,
            double growth,
            int successfulDeals,
            int pendingDeals,
            int rejectedDeals,
            int upcomingDeals,
            int dealsWon
    ) {}


    // =========================================================
    // PROFIT EARNED
    // =========================================================

    public record ProfitEarned(
            int currentYear,
            Map<String, ProfitYear> yearly
    ) {}


    public record ProfitYear(
            List<Integer> monthly
    ) {}


    // =========================================================
    // SALES EXECUTIVES
    // =========================================================

    public record SalesExecutive(
            int id,
            String name,
            String designation,
            String avatar,
            long revenue,
            int wonDeals,
            double conversionRate
    ) {}


    // =========================================================
    // RECENT DEALS
    // =========================================================

    public record RecentDeal(
            int id,
            String deal_name,
            String stage,
            String deal_value,
            String owner_name,
            String owner_img,
            String tags,
            String probability,
            String status
    ) {}


    // =========================================================
    // DEAL WON COMPANIES
    // =========================================================

    public record DealWonCompany(
            String name,
            String avatar
    ) {}

}