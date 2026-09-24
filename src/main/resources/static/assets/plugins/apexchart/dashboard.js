$(function () {

    window.addEventListener("dashboardDataReady", function () {

    const data = window.dashboardData;

    if (!data) {
        console.error("Dashboard data is missing.");
        return;
    }

    // Sales Analytics
    window.updateSalesPerformance(
        data.sales,
        data.monthlyTarget,
        "weekly"
    );

    // Traffic Sources
    loadTrafficSourcesChart(data);

    // Summary cards
    updateDashboardSummaryCards(data);

    // Performance
    renderSalesExecutivePerformance(data);

});

});

function renderSalesExecutivePerformance(data) {

    const currentUserContainer =
        document.getElementById("current-executive-performance");

    const executiveListContainer =
        document.getElementById("executive-performance-list");

    if (!currentUserContainer || !executiveListContainer) {
        return;
    }

    const executives =
    Array.isArray(data?.salesExecutives)
        ? data.salesExecutives
        : [];

if (executives.length === 0) {

    console.error("Sales Executive Performance data is missing.");

    return;
}

    const authenticatedUser = window.CrmsAuth?.getCurrentUser?.() || {};
    const currentUserId = Number(authenticatedUser.userId ?? authenticatedUser.id);

    /*
     * Find maximum values.
     * These are used to normalize the three
     * performance metrics.
     */

    const maxRevenue = Math.max(
        ...executives.map(
            executive => Number(executive.revenue || 0)
        )
    );

    const maxWonDeals = Math.max(
        ...executives.map(
            executive => Number(executive.wonDeals || 0)
        )
    );

    const maxConversion = Math.max(
        ...executives.map(
            executive => Number(executive.conversionRate || 0)
        )
    );

    /*
     * Calculate performance score.
     */

    const rankedExecutives = executives.map(executive => {

        const revenueScore =
            maxRevenue > 0
                ? (Number(executive.revenue) / maxRevenue) * 100
                : 0;

        const dealsScore =
            maxWonDeals > 0
                ? (Number(executive.wonDeals) / maxWonDeals) * 100
                : 0;

        const conversionScore =
            maxConversion > 0
                ? (Number(executive.conversionRate) / maxConversion) * 100
                : 0;

        const performanceScore =
            (revenueScore * 0.40) +
            (dealsScore * 0.35) +
            (conversionScore * 0.25);

        return {
            ...executive,
            performanceScore
        };

    });

    /*
     * Sort highest performance score first.
     */

    rankedExecutives.sort(
        (a, b) =>
            b.performanceScore - a.performanceScore
    );

    /*
     * Assign actual rank.
     */

    rankedExecutives.forEach(
        (executive, index) => {

            executive.rank = index + 1;

        }
    );

    /*
     * Find logged-in executive.
     */

    const currentExecutive =
        rankedExecutives.find(
            executive =>
                Number(executive.id) === currentUserId
        );

    /*
     * Format Indian currency.
     */

    const formatCurrency = value => {

        return new Intl.NumberFormat("en-IN", {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 0
        }).format(value);

    };

    /*
     * Render logged-in executive separately.
     */

   if (currentExecutive) {

    currentUserContainer.innerHTML = `

        <div class="d-flex align-items-center justify-content-between gap-2">

            <div class="d-flex align-items-center min-w-0">

                <div class="avatar avatar-sm border rounded-circle flex-shrink-0">

                    <img src="${currentExecutive.avatar}"
                         class="img-fluid w-auto h-auto"
                         alt="${currentExecutive.name}">

                </div>

                <div class="ms-2 min-w-0">

                    <div class="d-flex align-items-center gap-1">

                        <h6 class="fw-semibold text-truncate mb-0 fs-14">
                            ${currentExecutive.name}
                        </h6>

                        <span class="badge badge-soft-primary fs-10">
                            YOU
                        </span>

                    </div>

                    <p class="fs-11 text-muted mb-0">
                        ${currentExecutive.designation}
                    </p>

                </div>

            </div>

            <div class="text-end flex-shrink-0">

                <p class="fw-semibold text-dark mb-0 fs-14">
                    ${formatCurrency(currentExecutive.revenue)}
                </p>

                <span class="fs-11 text-muted">
                    Rank #${currentExecutive.rank}
                </span>

            </div>

        </div>


        <div class="row g-1 mt-2 pt-2 border-top">

            <div class="col-4">

                <p class="fs-11 text-muted mb-0">
                    Won Deals
                </p>

                <p class="fw-semibold mb-0 fs-13">
                    ${currentExecutive.wonDeals}
                </p>

            </div>


            <div class="col-4">

                <p class="fs-11 text-muted mb-0">
                    Conversion
                </p>

                <p class="fw-semibold mb-0 fs-13">
                    ${currentExecutive.conversionRate}%
                </p>

            </div>


            <div class="col-4 text-end">

                <p class="fs-11 text-muted mb-0">
                    Score
                </p>

                <p class="fw-semibold text-success mb-0 fs-13">
                    ${currentExecutive.performanceScore.toFixed(1)}
                </p>

            </div>

        </div>

    `;

} else {

        currentUserContainer.innerHTML = `
            <p class="mb-0 text-muted fs-13">
                Current sales executive not found.
            </p>
        `;

    }

    /*
     * Remove the logged-in executive from
     * the remaining list.
     */

    const otherExecutives =
        rankedExecutives.filter(
            executive =>
                Number(executive.id) !== currentUserId
        );

    /*
     * Render remaining executives according
     * to actual performance rank.
     */

    executiveListContainer.innerHTML =
        otherExecutives.map(executive => {

            return `

                <div class="d-flex align-items-center
                            justify-content-between
                            gap-2 mb-3">

                    <div class="d-flex align-items-center min-w-0">

                       <div class="me-2">

                           <span class="avatar avatar-sm
                              rounded-circle
                               border
                               d-flex align-items-center
                               justify-content-center
                               text-dark fw-semibold"
                           style="color: #212529 !important;">
 
                       ${executive.rank}

                     </span>

                    </div>

                        <div class="avatar avatar-sm
                                    border rounded-circle
                                    flex-shrink-0">

                            <img src="${executive.avatar}"
                                 class="img-fluid w-auto h-auto"
                                 alt="${executive.name}">

                        </div>

                        <div class="ms-2 min-w-0">

                            <h6 class="fw-medium
                                       text-truncate
                                       mb-0 fs-14">

                                ${executive.name}

                            </h6>

                            <p class="fs-12 text-muted mb-0">

                                ${executive.wonDeals} Won Deals

                            </p>

                        </div>

                    </div>

                    <div class="text-end flex-shrink-0">

                        <p class="fw-semibold
                                  text-dark mb-0">

                            ${formatCurrency(executive.revenue)}

                        </p>

                        <p class="fs-12 text-muted mb-0">

                            ${executive.conversionRate}% Conv.

                        </p>

                    </div>

                </div>

            `;

        }).join("");

}


function showComingSoonAlert(message) {

    // Create toast container if it doesn't exist
    let toastContainer =
        document.getElementById('genericToastContainer');

    if (!toastContainer) {

        toastContainer =
            document.createElement('div');

        toastContainer.id =
            'genericToastContainer';

        toastContainer.className =
            'toast-container position-fixed top-0 end-0 p-3';

        toastContainer.style.zIndex =
            '9999';

        document.body.appendChild(
            toastContainer
        );
    }


    // Create toast
    const toastElement =
        document.createElement('div');

    toastElement.className =
        'toast';

    toastElement.setAttribute(
        'role',
        'alert'
    );

    toastElement.setAttribute(
        'aria-live',
        'assertive'
    );

    toastElement.setAttribute(
        'aria-atomic',
        'true'
    );


    // Toast content
    toastElement.innerHTML = `

        <div class="toast-header">

            <i class="ti ti-info-circle text-primary me-2"></i>

            <strong class="me-auto">
                Coming Soon
            </strong>

            <button type="button"
                class="btn-close"
                data-bs-dismiss="toast"
                aria-label="Close">
            </button>

        </div>

        <div class="toast-body">
            ${message}
        </div>

    `;


    // Add toast to container
    toastContainer.appendChild(
        toastElement
    );


    // Make sure Bootstrap Toast is available
    if (typeof bootstrap !== 'undefined' &&
        bootstrap.Toast) {

        const toast =
            new bootstrap.Toast(
                toastElement,
                {
                    delay: 3500
                }
            );

        toast.show();


        // Remove element after it disappears
        toastElement.addEventListener(
            'hidden.bs.toast',
            function () {
                toastElement.remove();
            }
        );

    } else {

        // Fallback if Bootstrap JS isn't loaded
        toastElement.classList.add(
            'show'
        );

        setTimeout(
            function () {
                toastElement.remove();
            },
            3500
        );

    }

}

function updateDashboardSummaryCards(data) {

    const cards = data?.dashboardCards;
    const revenue = data?.revenueOverview;

    if (!cards) {
        console.error("Dashboard cards data is missing.");
        return;
    }

      // Revenue - current month till date
    const revenueElement =
        document.getElementById("dashboard-revenue");

    if (revenueElement && revenue?.currentMonthRevenue != null) {
        revenueElement.textContent =
            `₹${Number(revenue.currentMonthRevenue).toLocaleString("en-IN")}`;
    }

    // Active Deals
    const activeDealsElement =
        document.getElementById("dashboard-active-deals");

    if (activeDealsElement) {
        activeDealsElement.textContent = cards.activeDeals;
    }

    // Conversion Rate
    const conversionRateElement =
        document.getElementById("dashboard-conversion-rate");

    if (conversionRateElement) {
        conversionRateElement.textContent =
            `${cards.conversionRate}%`;
    }

    // Total Contacts
    const totalContactsElement =
        document.getElementById("dashboard-total-contacts");

    if (totalContactsElement) {
        totalContactsElement.textContent =
            cards.totalContacts;
    }
}

