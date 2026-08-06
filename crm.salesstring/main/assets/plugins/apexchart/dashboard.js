$(function () {

    if (typeof dashboardData !== "undefined") {
        loadTrafficSourcesChart(window.dashboardData);
        window.updateSalesPerformance(
            
            dashboardData.sales,
            dashboardData.monthlyTarget,
            "weekly"
        );

    }

});