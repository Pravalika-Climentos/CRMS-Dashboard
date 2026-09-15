package com.example.Dashboard_APIs.Mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.Dashboard_APIs.DTO.DashboardData;
import com.example.Dashboard_APIs.DTO.DashboardData.DashboardCards;
import com.example.Dashboard_APIs.DTO.DashboardData.DealWonCompany;
import com.example.Dashboard_APIs.DTO.DashboardData.DealsOverview;
import com.example.Dashboard_APIs.DTO.DashboardData.PipelineStage;
import com.example.Dashboard_APIs.DTO.DashboardData.PipelineStatistics;
import com.example.Dashboard_APIs.DTO.DashboardData.ProfitEarned;
import com.example.Dashboard_APIs.DTO.DashboardData.ProfitYear;
import com.example.Dashboard_APIs.DTO.DashboardData.RecentDeal;
import com.example.Dashboard_APIs.DTO.DashboardData.RevenueOverview;
import com.example.Dashboard_APIs.DTO.DashboardData.SalesExecutive;
import com.example.Dashboard_APIs.DTO.DashboardData.SalesPerformance;
import com.example.Dashboard_APIs.DTO.DashboardData.TrafficSources;

@Component
public class DashboardMapper {

    /*
     * ============================================================
     * 1. TRAFFIC SOURCES
     * ============================================================
     */

    public TrafficSources toTrafficSources(
            List<String> labels,
            List<Integer> series) {

        return new TrafficSources(
                labels,
                series
        );
    }


    /*
     * ============================================================
     * 2. SALES PERFORMANCE
     * ============================================================
     */

    public SalesPerformance toSalesPerformance(
            List<String> labels,
            List<Integer> revenue,
            int target) {

        return new SalesPerformance(
                labels,
                revenue,
                target
        );
    }


    /*
     * ============================================================
     * 3. REVENUE OVERVIEW
     * ============================================================
     */

    public RevenueOverview toRevenueOverview(
            List<Double> monthly,
            Double currentMonthRevenue) {

        return new RevenueOverview(
                monthly,
                currentMonthRevenue
        );
    }


    /*
     * ============================================================
     * 4. DASHBOARD CARDS
     * ============================================================
     */

    public DashboardCards toDashboardCards(
            long totalSales,
            int totalOrders,
            int newCustomers,
            double conversionRate,
            int activeDeals,
            int totalContacts) {

        return new DashboardCards(
                totalSales,
                totalOrders,
                newCustomers,
                conversionRate,
                activeDeals,
                totalContacts
        );
    }


    /*
     * ============================================================
     * 5. PIPELINE STATISTICS
     * ============================================================
     */

    public PipelineStage toPipelineStage(
            String name,
            int value,
            int deals) {

        return new PipelineStage(
                name,
                value,
                deals
        );
    }


    public PipelineStatistics toPipelineStatistics(
            List<PipelineStage> stages) {

        return new PipelineStatistics(
                stages
        );
    }


    /*
     * ============================================================
     * 6. DEALS OVERVIEW
     * ============================================================
     */

    public DealsOverview toDealsOverview(
            int totalDeals,
            double growth,
            int successfulDeals,
            int pendingDeals,
            int rejectedDeals,
            int upcomingDeals,
            int dealsWon) {

        return new DealsOverview(
                totalDeals,
                growth,
                successfulDeals,
                pendingDeals,
                rejectedDeals,
                upcomingDeals,
                dealsWon
        );
    }


    /*
     * ============================================================
     * 7. PROFIT EARNED
     * ============================================================
     */

    public ProfitYear toProfitYear(
            List<Integer> monthly) {

        return new ProfitYear(
                monthly
        );
    }


    public ProfitEarned toProfitEarned(
            int currentYear,
            Map<String, ProfitYear> yearly) {

        return new ProfitEarned(
                currentYear,
                yearly
        );
    }


    /*
     * ============================================================
     * 8. SALES EXECUTIVES
     * ============================================================
     */

    public SalesExecutive toSalesExecutive(
            int id,
            String name,
            String designation,
            String avatar,
            long revenue,
            int wonDeals,
            double conversionRate) {

        return new SalesExecutive(
                id,
                name,
                designation,
                avatar,
                revenue,
                wonDeals,
                conversionRate
        );
    }


    /*
     * ============================================================
     * 9. RECENT DEALS
     * ============================================================
     */

    public RecentDeal toRecentDeal(
            int id,
            String dealName,
            String stage,
            String dealValue,
            String ownerName,
            String ownerImage,
            String tags,
            String probability,
            String status) {

        return new RecentDeal(
                id,
                dealName,
                stage,
                dealValue,
                ownerName,
                ownerImage,
                tags,
                probability,
                status
        );
    }


    /*
     * ============================================================
     * 10. DEALS WON / COMPANIES
     * ============================================================
     */

    public DealWonCompany toDealWonCompany(
            String name,
            String avatar) {

        return new DealWonCompany(
                name,
                avatar
        );
    }


    /*
     * ============================================================
     * 11. COMPLETE DASHBOARD RESPONSE
     * ============================================================
     *
     * Mapper combines already calculated/fetched values.
     *
     * IMPORTANT:
     * No database calls.
     * No business calculations.
     * No repository logic.
     *
     * Those remain inside DashboardServiceImpl.
     */

    public DashboardData toDashboardData(
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
            List<DealWonCompany> dealWonCompanies) {

        return new DashboardData(
                trafficSources,
                salesPerformance,
                revenueOverview,
                dashboardCards,
                monthlyTarget,
                sales,
                pipelineStatistics,
                dealsOverview,
                profitEarned,
                salesExecutives,
                recentDeals,
                dealWonCompanies
        );
    }


    /*
     * ============================================================
     * UTILITY METHODS
     * ============================================================
     */

    public List<PipelineStage> toPipelineStages(
            List<PipelineStage> stages) {

        if (stages == null) {
            return List.of();
        }

        return stages.stream()
                .toList();
    }


    public List<SalesExecutive> toSalesExecutives(
            List<SalesExecutive> executives) {

        if (executives == null) {
            return List.of();
        }

        return executives.stream()
                .toList();
    }


    public List<RecentDeal> toRecentDeals(
            List<RecentDeal> deals) {

        if (deals == null) {
            return List.of();
        }

        return deals.stream()
                .toList();
    }


    public List<DealWonCompany> toDealWonCompanies(
            List<DealWonCompany> companies) {

        if (companies == null) {
            return List.of();
        }

        return companies.stream()
                .toList();
    }


    public Map<String, ProfitYear> toProfitYears(
            Map<String, ProfitYear> yearlyData) {

        if (yearlyData == null) {
            return Map.of();
        }

        return yearlyData.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }
}