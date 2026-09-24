const API_BASE_URL = "";

async function loadDashboardData() {

    try {

        const response = await fetch(
            `${API_BASE_URL}/api/dashboard`
        );

        if (!response.ok) {
            throw new Error(
                `Dashboard API failed: ${response.status}`
            );
        }

        const data = await response.json();

       window.dashboardData = data;

       console.log("Dashboard API data:", data);

       // Tell all dashboard components that API data is ready
       window.dispatchEvent(new CustomEvent("dashboardDataReady")
     );

      return data;

    } catch (error) {

        console.error(
            "Unable to load dashboard data:",
            error
        );

        return null;
    }
}

window.addEventListener('crms:dashboard-refresh', loadDashboardData);
