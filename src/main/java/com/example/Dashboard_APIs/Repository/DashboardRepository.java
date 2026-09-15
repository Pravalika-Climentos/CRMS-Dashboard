package com.example.Dashboard_APIs.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.CRM.Entity.Company;
import com.example.Dashboard_APIs.Repository.Projection.DashboardProjections.*;

public interface DashboardRepository extends JpaRepository<Company, Long> {

    // =========================================================
    // 1. DASHBOARD CARDS
    // =========================================================

   @Query(value = """
    SELECT
        (SELECT COALESCE(SUM(total_amount), 0)
         FROM sales_transactions) AS totalSales,

        (SELECT COUNT(*)
         FROM sales_transactions) AS totalOrders,

        (SELECT COUNT(*)
         FROM leads
         WHERE created_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')) AS newCustomers,

        (SELECT COUNT(*)
         FROM deals
         WHERE status = 'OPEN') AS activeDeals,

        (SELECT COUNT(*)
         FROM contacts) AS totalContacts
    """, nativeQuery = true)
    DashboardCardsProjection getDashboardCards();


    // =========================================================
    // 2. TOTAL SALES
    // =========================================================

    @Query(value = """
        SELECT COALESCE(SUM(total_amount), 0)
        FROM sales_transactions
        WHERE payment_status = 'PAID'
        """, nativeQuery = true)
    BigDecimal getTotalSales();


    // =========================================================
    // 3. TRAFFIC / LEAD SOURCES
    // =========================================================

    @Query(value = """
        SELECT
            ls.source_name AS sourceName,
            COUNT(l.lead_id) AS leadCount
        FROM lead_sources ls
        LEFT JOIN leads l
            ON l.source_id = ls.source_id
        WHERE ls.active = TRUE
        GROUP BY ls.source_id, ls.source_name
        ORDER BY leadCount DESC
        """, nativeQuery = true)
    List<TrafficSourceProjection> getTrafficSources();


    // =========================================================
    // 4. SALES PERFORMANCE - DAILY
    // =========================================================

    @Query(value = """
        SELECT
            st.transaction_date AS transactionDate,
            COALESCE(SUM(st.total_amount), 0) AS revenue
        FROM sales_transactions st
        WHERE st.transaction_date BETWEEN :startDate AND :endDate
          AND st.payment_status = 'PAID'
        GROUP BY st.transaction_date
        ORDER BY st.transaction_date
        """, nativeQuery = true)
    List<DailySalesProjection> getDailySales(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    // =========================================================
    // 5. REVENUE OVERVIEW - MONTHLY
    // =========================================================

    @Query(value = """
        SELECT
            YEAR(st.transaction_date) AS yearValue,
            MONTH(st.transaction_date) AS monthValue,
            COALESCE(SUM(st.total_amount), 0) AS revenue
        FROM sales_transactions st
        WHERE st.transaction_date >= :startDate
          AND st.transaction_date < :endDate
          AND st.payment_status = 'PAID'
        GROUP BY
            YEAR(st.transaction_date),
            MONTH(st.transaction_date)
        ORDER BY
            YEAR(st.transaction_date),
            MONTH(st.transaction_date)
        """, nativeQuery = true)
    List<MonthlyRevenueProjection> getMonthlyRevenue(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    // =========================================================
    // 6. PIPELINE STATISTICS
    // =========================================================

    @Query(value = """
        SELECT
            ds.stage_name AS stageName,
            COUNT(d.deal_id) AS dealCount,
            COALESCE(SUM(d.deal_value), 0) AS pipelineValue
        FROM deal_stages ds
        LEFT JOIN deals d
            ON d.stage_id = ds.stage_id
           AND d.status = 'OPEN'
        WHERE ds.active = TRUE
        GROUP BY
            ds.stage_id,
            ds.stage_name,
            ds.stage_order
        ORDER BY ds.stage_order
        """, nativeQuery = true)
    List<PipelineProjection> getPipelineStatistics();


    // =========================================================
    // 7. DEALS OVERVIEW
    // =========================================================

    @Query(value = """
        SELECT
            COUNT(*) AS totalDeals,

            SUM(CASE
                WHEN status = 'WON'
                THEN 1 ELSE 0
            END) AS successfulDeals,

            SUM(CASE
                WHEN status = 'OPEN'
                THEN 1 ELSE 0
            END) AS pendingDeals,

            SUM(CASE
                WHEN status = 'LOST'
                THEN 1 ELSE 0
            END) AS rejectedDeals,

            SUM(CASE
                WHEN status = 'OPEN'
                 AND expected_close_date > CURRENT_DATE
                THEN 1 ELSE 0
            END) AS upcomingDeals,

            SUM(CASE
                WHEN status = 'WON'
                THEN 1 ELSE 0
            END) AS dealsWon

        FROM deals
        """, nativeQuery = true)
    DealsOverviewProjection getDealsOverview();


    // =========================================================
    // 8. SALES EXECUTIVE PERFORMANCE
    // =========================================================

  @Query(value = """
    SELECT
        u.user_id AS userId,
        u.full_name AS fullName,
        u.designation AS designation,
        u.avatar AS avatar,

        COALESCE(sr.revenue, 0) AS revenue,
        COALESCE(dw.wonDeals, 0) AS wonDeals,

        COALESCE(
            dw.wonDeals * 100.0 / NULLIF(dt.totalDeals, 0),
            0
        ) AS conversionRate

    FROM users u

    LEFT JOIN (
        SELECT
            sales_user_id,
            SUM(total_amount) AS revenue
        FROM sales_transactions
        WHERE payment_status = 'PAID'
        GROUP BY sales_user_id
    ) sr
        ON sr.sales_user_id = u.user_id

    LEFT JOIN (
        SELECT
            owner_user_id,
            COUNT(*) AS wonDeals
        FROM deals
        WHERE status = 'WON'
        GROUP BY owner_user_id
    ) dw
        ON dw.owner_user_id = u.user_id

    LEFT JOIN (
        SELECT
            owner_user_id,
            COUNT(*) AS totalDeals
        FROM deals
        GROUP BY owner_user_id
    ) dt
        ON dt.owner_user_id = u.user_id

    WHERE u.active = TRUE

    ORDER BY revenue DESC
    """, nativeQuery = true)
    List<SalesExecutiveProjection> getSalesExecutivePerformance();


    // =========================================================
    // 9. RECENT DEALS
    // =========================================================

    @Query(value = """
        SELECT
            d.deal_id AS dealId,
            d.deal_name AS dealName,
            ds.stage_name AS stage,
            d.deal_value AS dealValue,
            u.full_name AS ownerName,
            u.avatar AS ownerImage,
            d.probability AS probability,
            d.status AS status
        FROM deals d
        JOIN deal_stages ds
            ON ds.stage_id = d.stage_id
        JOIN users u
            ON u.user_id = d.owner_user_id
        ORDER BY d.created_at DESC
        LIMIT 5
        """, nativeQuery = true)
    List<RecentDealProjection> getRecentDeals();


    // =========================================================
    // 10. WON COMPANIES
    // =========================================================

    @Query(value = """
        SELECT
            c.company_id AS companyId,
            c.company_name AS companyName
        FROM deals d
        JOIN companies c
            ON c.company_id = d.company_id
        WHERE d.status = 'WON'
        GROUP BY
            c.company_id,
            c.company_name
        ORDER BY MAX(d.won_date) DESC
        LIMIT 5
        """, nativeQuery = true)
    List<WonCompanyProjection> getWonCompanies();


    // =========================================================
    // 11. PROFIT / SALES BY YEAR AND MONTH
    // =========================================================

    @Query(value = """
        SELECT
            YEAR(st.transaction_date) AS yearValue,
            MONTH(st.transaction_date) AS monthValue,
            COALESCE(
                SUM(st.total_amount - st.cost_amount),
                0
            ) AS profit
        FROM sales_transactions st
        WHERE st.transaction_date >= :startDate
          AND st.transaction_date < :endDate
          AND st.payment_status = 'PAID'
        GROUP BY
            YEAR(st.transaction_date),
            MONTH(st.transaction_date)
        ORDER BY
            YEAR(st.transaction_date),
            MONTH(st.transaction_date)
        """, nativeQuery = true)
    List<MonthlyProfitProjection> getMonthlyProfit(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(value = """
    SELECT
        COUNT(*) AS totalLeads,
        SUM(
            CASE
                WHEN converted = TRUE THEN 1
                ELSE 0
            END
        ) AS convertedLeads
    FROM leads
    """, nativeQuery = true)
ConversionRateProjection getConversionRate();

}