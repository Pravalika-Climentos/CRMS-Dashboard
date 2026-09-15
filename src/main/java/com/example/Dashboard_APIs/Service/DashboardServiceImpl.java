package com.example.Dashboard_APIs.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Dashboard_APIs.Repository.DashboardRepository;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.ConversionRateProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.DailySalesProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.DashboardCardsProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.DealsOverviewProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.MonthlyProfitProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.MonthlyRevenueProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.PipelineProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.RecentDealProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.SalesExecutiveProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.TrafficSourceProjection;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.WonCompanyProjection;
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
import com.example.Dashboard_APIs.Mapper.DashboardMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;
    private final DashboardMapper dashboardMapper;

    /*
     * Dashboard target.
     *
     * Later this should come from a configurable CRM setting/table.
     * For now it is kept here so the dashboard can work with the
     * existing frontend contract.
     */
    private static final int MONTHLY_TARGET = 1_000_000;

    @Override
    public DashboardData getDashboardData() {

        LocalDate today = LocalDate.now();

        /*
         * ---------------------------------------------------------
         * 1. DASHBOARD CARDS
         * ---------------------------------------------------------
         */

        DashboardCardsProjection cards =
                dashboardRepository.getDashboardCards();

        long totalSales = cards != null && cards.getTotalSales() != null
                ? cards.getTotalSales().longValue()
                : 0L;

        int totalOrders = cards != null && cards.getTotalOrders() != null
                ? cards.getTotalOrders().intValue()
                : 0;

        int newCustomers = cards != null && cards.getNewCustomers() != null
                ? cards.getNewCustomers().intValue()
                : 0;

        int activeDeals = cards != null && cards.getActiveDeals() != null
                ? cards.getActiveDeals().intValue()
                : 0;

        int totalContacts = cards != null && cards.getTotalContacts() != null
                ? cards.getTotalContacts().intValue()
                : 0;

        /*
         * Conversion rate:
         *
         * converted leads / total leads * 100
         *
         * We calculate this separately rather than multiplying rows
         * through multiple joins.
         */
        double conversionRate = calculateConversionRate();


        DashboardCards dashboardCards =
                dashboardMapper.toDashboardCards(
                        totalSales,
                        totalOrders,
                        newCustomers,
                        conversionRate,
                        activeDeals,
                        totalContacts
                );


        /*
         * ---------------------------------------------------------
         * 2. TRAFFIC SOURCES
         * ---------------------------------------------------------
         */

        List<TrafficSourceProjection> trafficRows =
                dashboardRepository.getTrafficSources();

        List<String> trafficLabels = trafficRows.stream()
                .map(TrafficSourceProjection::getSourceName)
                .toList();

        List<Integer> trafficSeries = trafficRows.stream()
                .map(row -> safeInt(row.getLeadCount()))
                .toList();

        TrafficSources trafficSources =
                dashboardMapper.toTrafficSources(
                        trafficLabels,
                        trafficSeries
                );


        /*
         * ---------------------------------------------------------
         * 3. SALES PERFORMANCE
         *
         * Current week: Monday -> Sunday
         * ---------------------------------------------------------
         */

        LocalDate weekStart =
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        LocalDate weekEnd =
                weekStart.plusDays(6);

        List<DailySalesProjection> dailySales =
                dashboardRepository.getDailySales(
                        weekStart,
                        weekEnd
                );

        Map<LocalDate, BigDecimal> dailySalesMap =
                dailySales.stream()
                        .collect(Collectors.toMap(
                                DailySalesProjection::getTransactionDate,
                                row -> safeDecimal(row.getRevenue())
                        ));

        List<String> performanceLabels = new ArrayList<>();
        List<Integer> performanceRevenue = new ArrayList<>();

        for (LocalDate date = weekStart;
             !date.isAfter(weekEnd);
             date = date.plusDays(1)) {

            performanceLabels.add(
                    date.getDayOfWeek()
                            .getDisplayName(
                                    java.time.format.TextStyle.SHORT,
                                    java.util.Locale.ENGLISH
                            )
            );

            performanceRevenue.add(
                    safeInt(
                            dailySalesMap.getOrDefault(
                                    date,
                                    BigDecimal.ZERO
                            )
                    )
            );
        }

        int dailyTarget = MONTHLY_TARGET / today.lengthOfMonth();

        SalesPerformance salesPerformance =
                dashboardMapper.toSalesPerformance(
                        performanceLabels,
                        performanceRevenue,
                        dailyTarget
                );


        /*
         * ---------------------------------------------------------
         * 4. REVENUE OVERVIEW
         *
         * Last 6 months
         * ---------------------------------------------------------
         */

        YearMonth currentMonth =
                YearMonth.from(today);

        YearMonth sixMonthsAgo =
                currentMonth.minusMonths(5);

        LocalDate revenueStart =
                sixMonthsAgo.atDay(1);

        LocalDate revenueEnd =
                currentMonth.plusMonths(1).atDay(1);

        List<MonthlyRevenueProjection> revenueRows =
                dashboardRepository.getMonthlyRevenue(
                        revenueStart,
                        revenueEnd
                );

        Map<String, BigDecimal> revenueMap =
                new LinkedHashMap<>();

        for (MonthlyRevenueProjection row : revenueRows) {

            String key =
                    row.getYearValue() + "-" +
                    String.format("%02d", row.getMonthValue());

            revenueMap.put(
                    key,
                    safeDecimal(row.getRevenue())
            );
        }

        List<Double> monthlyRevenue =
                new ArrayList<>();

        for (int i = 0; i < 6; i++) {

            YearMonth month =
                    sixMonthsAgo.plusMonths(i);

            String key =
                    month.getYear() + "-" +
                    String.format("%02d", month.getMonthValue());

            monthlyRevenue.add(
                    revenueMap
                            .getOrDefault(key, BigDecimal.ZERO)
                            .doubleValue()
            );
        }

        String currentRevenueKey =
                currentMonth.getYear() + "-" +
                String.format("%02d", currentMonth.getMonthValue());

        Double currentMonthRevenue =
                revenueMap
                        .getOrDefault(
                                currentRevenueKey,
                                BigDecimal.ZERO
                        )
                        .doubleValue();

        RevenueOverview revenueOverview =
                dashboardMapper.toRevenueOverview(
                        monthlyRevenue,
                        currentMonthRevenue
                );


        /*
         * ---------------------------------------------------------
         * 5. MONTHLY SALES ARRAY
         *
         * Used by the existing Sales Performance chart.
         * ---------------------------------------------------------
         */

        LocalDate monthStart =
                currentMonth.atDay(1);

        LocalDate monthEnd =
                currentMonth.atEndOfMonth();

        List<DailySalesProjection> monthSalesRows =
                dashboardRepository.getDailySales(
                        monthStart,
                        monthEnd
                );

        Map<LocalDate, BigDecimal> monthSalesMap =
                monthSalesRows.stream()
                        .collect(Collectors.toMap(
                                DailySalesProjection::getTransactionDate,
                                row -> safeDecimal(row.getRevenue())
                        ));

        List<Integer> sales =
                new ArrayList<>();

        for (int day = 1;
             day <= currentMonth.lengthOfMonth();
             day++) {

            LocalDate date =
                    currentMonth.atDay(day);

            sales.add(
                    safeInt(
                            monthSalesMap.getOrDefault(
                                    date,
                                    BigDecimal.ZERO
                            )
                    )
            );
        }


        /*
         * ---------------------------------------------------------
         * 6. PIPELINE STATISTICS
         * ---------------------------------------------------------
         */

        List<PipelineProjection> pipelineRows =
                dashboardRepository.getPipelineStatistics();

        List<PipelineStage> pipelineStages =
                pipelineRows.stream()
                        .map(row ->
                                dashboardMapper.toPipelineStage(
                                        row.getStageName(),
                                        safeInt(row.getPipelineValue()),
                                        safeInt(row.getDealCount())
                                )
                        )
                        .toList();

        PipelineStatistics pipelineStatistics =
                dashboardMapper.toPipelineStatistics(
                        pipelineStages
                );


        /*
         * ---------------------------------------------------------
         * 7. DEALS OVERVIEW
         * ---------------------------------------------------------
         */

        DealsOverviewProjection dealOverviewRow =
                dashboardRepository.getDealsOverview();

        int totalDeals =
                safeInt(dealOverviewRow.getTotalDeals());

        int successfulDeals =
                safeInt(dealOverviewRow.getSuccessfulDeals());

        int pendingDeals =
                safeInt(dealOverviewRow.getPendingDeals());

        int rejectedDeals =
                safeInt(dealOverviewRow.getRejectedDeals());

        int upcomingDeals =
                safeInt(dealOverviewRow.getUpcomingDeals());

        int dealsWon =
                safeInt(dealOverviewRow.getDealsWon());

        double growth =
                calculateDealGrowth(
                        successfulDeals,
                        totalDeals
                );

        DealsOverview dealsOverview =
                dashboardMapper.toDealsOverview(
                        totalDeals,
                        growth,
                        successfulDeals,
                        pendingDeals,
                        rejectedDeals,
                        upcomingDeals,
                        dealsWon
                );


        /*
         * ---------------------------------------------------------
         * 8. PROFIT EARNED
         * ---------------------------------------------------------
         */

        LocalDate profitStart =
                LocalDate.of(
                        today.getYear() - 2,
                        1,
                        1
                );

        LocalDate profitEnd =
                LocalDate.of(
                        today.getYear() + 1,
                        1,
                        1
                );

        List<MonthlyProfitProjection> profitRows =
                dashboardRepository.getMonthlyProfit(
                        profitStart,
                        profitEnd
                );

        Map<String, Map<Integer, BigDecimal>> profitMap =
                new LinkedHashMap<>();

        for (MonthlyProfitProjection row : profitRows) {

            String year =
                    String.valueOf(row.getYearValue());

            profitMap
                    .computeIfAbsent(
                            year,
                            key -> new LinkedHashMap<>()
                    )
                    .put(
                            row.getMonthValue(),
                            safeDecimal(row.getProfit())
                    );
        }

        Map<String, ProfitYear> yearlyProfit =
                new LinkedHashMap<>();

        for (int year = today.getYear() - 2;
             year <= today.getYear();
             year++) {

            Map<Integer, BigDecimal> monthly =
                    profitMap.getOrDefault(
                            String.valueOf(year),
                            Collections.emptyMap()
                    );

            List<Integer> values =
                    new ArrayList<>();

            for (int month = 1; month <= 12; month++) {

                values.add(
                        safeInt(
                                monthly.getOrDefault(
                                        month,
                                        BigDecimal.ZERO
                                )
                        )
                );
            }

            yearlyProfit.put(
                    String.valueOf(year),
                    dashboardMapper.toProfitYear(values)
            );
        }

        ProfitEarned profitEarned =
                dashboardMapper.toProfitEarned(
                        today.getYear(),
                        yearlyProfit
                );


        /*
         * ---------------------------------------------------------
         * 9. SALES EXECUTIVES
         * ---------------------------------------------------------
         */

        List<SalesExecutiveProjection> executiveRows =
                dashboardRepository.getSalesExecutivePerformance();

        List<SalesExecutive> salesExecutives =
                executiveRows.stream()
                        .map(row ->
                                dashboardMapper.toSalesExecutive(
                                        safeInt(row.getUserId()),
                                        row.getFullName(),
                                        row.getDesignation(),
                                        row.getAvatar(),
                                        safeLong(row.getRevenue()),
                                        safeInt(row.getWonDeals()),
                                        safeDouble(row.getConversionRate())
                                )
                        )
                        .toList();


        /*
         * ---------------------------------------------------------
         * 10. RECENT DEALS
         * ---------------------------------------------------------
         */

        List<RecentDealProjection> recentDealRows =
                dashboardRepository.getRecentDeals();

        List<RecentDeal> recentDeals =
                recentDealRows.stream()
                        .map(row ->
                                dashboardMapper.toRecentDeal(
                                        safeInt(row.getDealId()),
                                        row.getDealName(),
                                        row.getStage(),
                                        formatCurrency(row.getDealValue()),
                                        row.getOwnerName(),
                                        row.getOwnerImage(),
                                        "0",
                                        formatPercentage(row.getProbability()),
                                        row.getStatus()
                                )
                        )
                        .toList();


        /*
         * ---------------------------------------------------------
         * 11. WON COMPANIES
         * ---------------------------------------------------------
         */

        List<WonCompanyProjection> wonCompanyRows =
                dashboardRepository.getWonCompanies();

        List<DealWonCompany> dealWonCompanies =
                wonCompanyRows.stream()
                        .map(row ->
                                dashboardMapper.toDealWonCompany(
                                        row.getCompanyName(),
                                        null
                                )
                        )
                        .toList();


        /*
         * ---------------------------------------------------------
         * FINAL RESPONSE
         * ---------------------------------------------------------
         */

        return dashboardMapper.toDashboardData(
                trafficSources,
                salesPerformance,
                revenueOverview,
                dashboardCards,
                MONTHLY_TARGET,
                sales,
                pipelineStatistics,
                dealsOverview,
                profitEarned,
                salesExecutives,
                recentDeals,
                dealWonCompanies
        );
    }


    // =============================================================
    // PRIVATE CALCULATION METHODS
    // =============================================================

    private double calculateConversionRate() {

    ConversionRateProjection result =
            dashboardRepository.getConversionRate();

    if (result == null
            || result.getTotalLeads() == null
            || result.getTotalLeads() == 0) {

        return 0.0;
    }

    long total =
            result.getTotalLeads();

    long converted =
            result.getConvertedLeads() == null
                    ? 0
                    : result.getConvertedLeads();

    return round(
            (converted * 100.0) / total
      );
   }


    private double calculateDealGrowth(
            int successfulDeals,
            int totalDeals) {

        if (totalDeals == 0) {
            return 0.0;
        }

        return round(
                (successfulDeals * 100.0) / totalDeals
        );
    }


    private BigDecimal safeDecimal(BigDecimal value) {

        return value == null
                ? BigDecimal.ZERO
                : value;
    }


    private int safeInt(Number value) {

        return value == null
                ? 0
                : value.intValue();
    }


    private long safeLong(Number value) {

        return value == null
                ? 0L
                : value.longValue();
    }


    private double safeDouble(Number value) {

        return value == null
                ? 0.0
                : value.doubleValue();
    }


    private String formatCurrency(BigDecimal value) {

        if (value == null) {
            return "$0";
        }

        return "$" + value
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }


    private String formatPercentage(BigDecimal value) {

        if (value == null) {
            return "0%";
        }

        return value
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }


    private double round(double value) {

        return BigDecimal
                .valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}