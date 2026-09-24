$(document).ready(function() {

    window.addEventListener("dashboardDataReady", function () {

    if ($('#deal-project').length === 0) {
        return;
    }

    const recentDeals =
        window.dashboardData?.recentDeals || [];

    if (!Array.isArray(recentDeals) || recentDeals.length === 0) {

        console.warn("Recent Deals data is not available.");

        return;
    }

    console.log("Recent Deals from backend:", recentDeals);


    if ($.fn.DataTable.isDataTable('#deal-project')) $('#deal-project').DataTable().clear().destroy();
    $('#deal-project').DataTable({

        "bFilter": false,
        "bInfo": false,
        "ordering": false,
        "paging": false,

        // BACKEND DATA
        "data": recentDeals,

        "columns": [

            // ---------------------------------------
            // Deal Name
            // ---------------------------------------
            {
                "render": function (data, type, row) {

                    return '<a href="deals_details.html" class="fw-medium">'
                        + row.deal_name
                        + '</a>';

                }
            },


            // ---------------------------------------
            // Stage
            // ---------------------------------------
            {
                "data": "stage"
            },


            // ---------------------------------------
            // Deal Value
            // ---------------------------------------
            {
                "data": "deal_value"
            },


            // ---------------------------------------
            // Tags
            // ---------------------------------------
            {
                "render": function (data, type, row) {

                    let className = "";
                    let tagName = "";

                    if (row.tags == "0") {

                        className =
                            "badge-soft-secondary border-secondary";

                        tagName = "Rated";

                    } else if (row.tags == "1") {

                        className =
                            "badge-soft-success border-success";

                        tagName = "Collab";

                    } else if (row.status == "2") {

                        className =
                            "badge-soft-danger border-danger";

                        tagName = "Rejected";

                    } else {

                        className =
                            "badge-soft-purple border-purple";

                        tagName = "Promotion";

                    }

                    return '<span class="badge badge-pill border '
                        + className
                        + '">'
                        + tagName
                        + '</span>';

                }
            },


            // ---------------------------------------
            // Owner
            // ---------------------------------------
            {
                "render": function (data, type, row) {

                    return `
                        <p class="d-flex align-items-center fs-14 mb-0">

                            <a href="#"
                               class="avatar avatar-sm avatar-rounded border me-2">

                                <img class="img-fluid"
                                     src="${row.owner_img}"
                                     alt="${row.owner_name}">

                            </a>

                            <a href="#">
                                ${row.owner_name}
                            </a>

                        </p>
                    `;

                }
            },


            // ---------------------------------------
            // Probability
            // ---------------------------------------
            {
                "render": function (data, type, row) {

                    return `
                        <p class="text-dark mb-0">
                            ${row.probability}
                        </p>
                    `;

                }
            },


            // ---------------------------------------
            // Status
            // ---------------------------------------
            {
                "render": function (data, type, row) {

                    let className = "";
                    let statusName = "";

                    if (row.status == "0") {

                        className = "bg-indigo";
                        statusName = "Open";

                    } else if (row.status == "1") {

                        className = "bg-danger";
                        statusName = "Lost";

                    } else if (row.status == "2") {

                        className = "bg-indigo";
                        statusName = "Open";

                    } else {

                        className = "bg-success";
                        statusName = "Won";

                    }

                    return `
                        <span class="badge badge-pill ${className}">
                            ${statusName}
                        </span>
                    `;

                }
            }

        ]

    });

});

    if ($('#recent-deals').length > 0) {
        if ($.fn.DataTable.isDataTable('#recent-deals')) $('#recent-deals').DataTable().clear().destroy();
        $('#recent-deals').DataTable({
            "bFilter": false,
            "bInfo": false,
            "ordering": false,
            "paging": false,
            "data": [{
                    "id": 1,
                    "deal_name": "SkyHigh Annual Booking",
                    "stage": "Appointment",
                    "deal_value": "$78,11,800",
                    "status": "0"
                },
                {
                    "id": 2,
                    "deal_name": "CRM Onboarding Package",
                    "stage": "Appointment",
                    "deal_value": "$72,11,289",
                    "status": "1"
                },
                {
                    "id": 3,
                    "deal_name": "Enterprise Plan Upgrade",
                    "stage": "Appointment",
                    "deal_value": "$16,11,457",
                    "status": "0"
                },
                {
                    "id": 4,
                    "deal_name": "CRM Migration Project",
                    "stage": "Appointment",
                    "deal_value": "$85,11,789",
                    "status": "0"
                },
                {
                    "id": 5,
                    "deal_name": "Project Management",
                    "stage": "Appointment",
                    "deal_value": "$65,12,589",
                    "status": "0"
                },
            ],
            "columns": [{
                    "render": function(data, type, row) {
                        return '<p class="text-dark fw-medium mb-1"><a href="deals_details.html">' + row['deal_name'] + '</a></p><p class="mb-0">' + row['stage'] + '</p>';
                    }
                },
                {
                    "render": function(data, type, row) {
                        return '<p class="text-dark mb-0">' + row['deal_value'] + '</p>';
                    }
                },
                {
                    "render": function(data, type, row) {
                        if (row['status'] == "0") {
                            var class_name = "bg-soft-success text-success";
                            var status_name = "Won"
                        } else {
                            var class_name = "bg-soft-danger text-danger";
                            var status_name = "Lost"
                        }
                        return '<span class="badge badge-pill  ' + class_name + '" >' + status_name + '</span>';
                    }
                },
            ]
        });
    }

    if ($('#executive-project').length > 0) {
        if ($.fn.DataTable.isDataTable('#executive-project')) $('#executive-project').DataTable().clear().destroy();
        $('#executive-project').DataTable({
            "bFilter": false,
            "bInfo": false,
            "ordering": false,
            "paging": false,
            "data": [{
                    "id": 1,
                    "executive_img": "assets/img/profiles/avatar-25.jpg",
                    "executive_name": "Robert Johnson",
                    "deal": "98",
                    "deal-status": "0",
                    "revenue": "$7500",
                    "conversion": "0",
                    "conversion_name": "100%",
                    "status": "0",
                },
                {
                    "id": 2,
                    "executive_img": "assets/img/profiles/avatar-04.jpg",
                    "executive_name": "Isabella Cooper",
                    "deal": "87",
                    "deal-status": "0",
                    "revenue": "$2000",
                    "conversion": "0",
                    "conversion_name": "100%",
                    "status": "0",
                },
                {
                    "id": 3,
                    "executive_img": "assets/img/profiles/avatar-27.jpg",
                    "executive_name": "John Smith",
                    "deal": "56",
                    "deal-status": "1",
                    "revenue": "$1600",
                    "conversion": "1",
                    "conversion_name": "85%",
                    "status": "1",
                },
                {
                    "id": 4,
                    "executive_img": "assets/img/profiles/avatar-07.jpg",
                    "executive_name": "Sophia Parker",
                    "deal": "10",
                    "deal-status": "2",
                    "revenue": "$600",
                    "conversion": "2",
                    "conversion_name": "30%",
                    "status": "2",
                },
                {
                    "id": 5,
                    "executive_img": "assets/img/profiles/avatar-08.jpg",
                    "executive_name": "Ethan Reynolds",
                    "deal": "87",
                    "deal-status": "0",
                    "revenue": "$2800",
                    "conversion": "0",
                    "conversion_name": "100%",
                    "status": "0",
                },
                {
                    "id": 6,
                    "executive_img": "assets/img/profiles/avatar-09.jpg",
                    "executive_name": "Liam Carter",
                    "deal": "87",
                    "deal-status": "1",
                    "revenue": "$6955",
                    "conversion": "1",
                    "conversion_name": "85%",
                    "status": "2",
                },
            ],
            "columns": [{
                    "render": function(data, type, row) {
                        return '<p class="d-flex align-items-center fs-14 mb-0"><a href="#" class="avatar avatar-sm avatar-rounded border me-2"><img class="img-fluid" src="' + row['executive_img'] + '" alt="User Image"></a><a href="#">' + row['executive_name'] + '</a></p>';
                    }
                },
                {
                    "render": function(data, type, row) {
                        // 1. Initialize variables at the top so they are always "defined"
                        var class_name = "";
                        var deal_text = row['deal'] ? row['deal'] : "";

                        // 2. Assign classes based on the status
                        if (row['deal-status'] == "0") {
                            class_name = "text-success";
                        } else if (row['deal-status'] == "1") {
                            class_name = "text-info";
                        } else {
                            class_name = "text-danger";
                        }

                        return '<p class="fw-medium mb-0 ' + class_name + '">' + deal_text + '</p>';
                    }
                },
                {
                    "data": "revenue"
                },
                {
                    "render": function(data, type, row) {
                        // 1. Initialize variables at the top so they are always "defined"
                        var class_name = "";
                        var conv_text = row['conversion_name'] ? row['conversion_name'] : "";

                        // 2. Assign classes based on the status
                        if (row['conversion'] == "0") {
                            class_name = "badge-soft-success";
                        } else if (row['conversion'] == "1") {
                            class_name = "badge-soft-info";
                        } else {
                            class_name = "badge-soft-primary";
                        }

                        return '<span class="badge badge-pill ' + class_name + '">' + conv_text + '</span>';
                    }
                },
                {
                    "render": function(data, type, row) {
                        if (row['status'] == "0") {
                            var class_name = "bg-success text-white";
                            var status_name = "Excellent"
                        } else if (row['status'] == "1") {
                            var class_name = "bg-info text-white";
                            var status_name = "Good"
                        } else {
                            var class_name = "bg-danger text-white";
                            var status_name = "Average"
                        }
                        return '<span class="badge badge-pill  ' + class_name + '" >' + status_name + '</span>';
                    }
                },
            ]
        });
    }

});
