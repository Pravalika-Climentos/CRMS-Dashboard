package com.example.Dashboard_APIs.Repository.Projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DashboardProjections {

    private DashboardProjections() {
    }


    public interface DashboardCardsProjection {

        BigDecimal getTotalSales();

        Long getTotalOrders();

        Long getNewCustomers();

        Long getActiveDeals();

        Long getTotalContacts();
    }


    public interface TrafficSourceProjection {

        String getSourceName();

        Long getLeadCount();
    }


    public interface DailySalesProjection {

        LocalDate getTransactionDate();

        BigDecimal getRevenue();
    }


    public interface MonthlyRevenueProjection {

        Integer getYearValue();

        Integer getMonthValue();

        BigDecimal getRevenue();
    }


    public interface PipelineProjection {

        String getStageName();

        Long getDealCount();

        BigDecimal getPipelineValue();
    }


    public interface DealsOverviewProjection {

        Long getTotalDeals();

        Long getSuccessfulDeals();

        Long getPendingDeals();

        Long getRejectedDeals();

        Long getUpcomingDeals();

        Long getDealsWon();
    }


    public interface SalesExecutiveProjection {

        Long getUserId();

        String getFullName();

        String getDesignation();

        String getAvatar();

        BigDecimal getRevenue();

        Long getWonDeals();

        Double getConversionRate();
    }


    public interface RecentDealProjection {

        Long getDealId();

        String getDealName();

        String getStage();

        BigDecimal getDealValue();

        String getOwnerName();

        String getOwnerImage();

        BigDecimal getProbability();

        String getStatus();
    }


    public interface WonCompanyProjection {

        Long getCompanyId();

        String getCompanyName();
    }


    public interface MonthlyProfitProjection {

        Integer getYearValue();

        Integer getMonthValue();

        BigDecimal getProfit();
    }

    public interface ConversionRateProjection {

    Long getTotalLeads();

    Long getConvertedLeads();
}
}
