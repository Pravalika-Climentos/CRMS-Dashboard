// Demo Dashboard Data
window.dashboardData = {

    trafficSources: {
        labels: [
            "A",
            "B",
            "C",
            "D"
        ],
        series: [
            12,
            12,
            18,
            19
        ]
    },

    salesPerformance: {
        labels: ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"],
        revenue: [45000,52000,38000,61000,56000,72000,68000],
        target: 50000
    },

    revenueOverview: {
        monthly: [4.2,5.1,6.3,7.8,8.2,9.5]
    },

    dashboardCards: {
        totalSales: 1845620,
        totalOrders: 1245,
        newCustomers: 168,
        conversionRate: 24.5
    },
     





    
    monthlyTarget: 1000000,

    sales: [
        12000, 52000, 38000, 60000, 42000, 55000, 62000,
        48000, 70000, 59000, 52000, 68000, 71000, 65000,
        78000, 60000, 72000, 75000, 69000, 80000, 73000,
        85000, 78000, 90000, 82000, 95000, 87000, 98000,
        92000, 105000, 110000
    ],

    salesExecutives: {

    // Temporary demo user.
    // Later this will come from the actual logged-in user/session.
    currentUserId: "SE002",

    executives: [

        {
            id: "SE001",
            name: "Robert Johnson",
            designation: "Sales Executive",
            avatar: "assets/img/profiles/avatar-01.jpg",
            revenue: 1250000,
            wonDeals: 32,
            conversionRate: 78
        },

        {
            id: "SE002",
            name: "Isabella Cooper",
            designation: "Sales Executive",
            avatar: "assets/img/profiles/avatar-04.jpg",
            revenue: 1180000,
            wonDeals: 29,
            conversionRate: 74
        },

        {
            id: "SE003",
            name: "John Smith",
            designation: "Sales Executive",
            avatar: "assets/img/profiles/avatar-03.jpg",
            revenue: 1020000,
            wonDeals: 27,
            conversionRate: 71
        },

        {
            id: "SE004",
            name: "Sophia Parker",
            designation: "Sales Executive",
            avatar: "assets/img/profiles/avatar-08.jpg",
            revenue: 890000,
            wonDeals: 24,
            conversionRate: 68
        },

        {
            id: "SE005",
            name: "Ethan Reynolds",
            designation: "Sales Executive",
            avatar: "assets/img/profiles/avatar-05.jpg",
            revenue: 760000,
            wonDeals: 21,
            conversionRate: 64
        },

        {
            id: "SE006",
            name: "Liam Carter",
            designation: "Sales Executive",
            avatar: "assets/img/profiles/avatar-06.jpg",
            revenue: 680000,
            wonDeals: 19,
            conversionRate: 61
        }

    ]
},
    pipelineStatistics: {
    stages: [
        {
            name: "Lead",
            amount: 20010,
            deals: 80
        },
        {
            name: "Proposal",
            amount: 17210,
            deals: 23
        },
        {
            name: "Sales",
            amount: 9210,
            deals: 12
        },
        {
            name: "Won",
            amount: 8210,
            deals: 21
        }
    ]
},

dealsOverview: {
    statuses: [
        {
            name: "Successful Deals",
            count: 1000
        },
        {
            name: "Pending Deals",
            count: 1056
        },
        {
            name: "Rejected Deals",
            count: 500
        },
        {
            name: "Upcoming Deals",
            count: 100
        }
    ],

    comparisonPercent: 12.5,

    dealsWon: 689,

    companyAvatars: [
        "assets/img/company/company-09.svg",
        "assets/img/company/company-10.svg",
        "assets/img/company/company-01.svg",
        "assets/img/company/company-02.svg",
        "assets/img/company/company-11.svg"
    ]
},
profitEarned: {
    currentYear: 2025,

    yearly: {
        2025: {
           monthly: [30, 35, 38, 90, 40, 38, 30, 20, 30, 80, 85, 85]
        },

        2024: {
            monthly: [10, 13, 16, 30, 18, 15, 12, 8, 13, 30, 33, 35]
        },

        2023: {
            monthly: [80, 11, 14, 25, 16, 13, 10, 7, 11, 25, 28, 30]
        }
    }
},

recentDeals: [
    {
        id: 1,
        deal_name: "Annual Software",
        stage: "Appointment",
        deal_value: "$19,94,938",
        owner_name: "Robert Johnson",
        owner_img: "assets/img/profiles/avatar-21.jpg",
        tags: "0",
        probability: "90%",
        status: "3"
    },
    {
        id: 2,
        deal_name: "CRM Onboarding",
        stage: "Appointment",
        deal_value: "$15,44,540",
        owner_name: "Isabella Cooper",
        owner_img: "assets/img/profiles/avatar-04.jpg",
        tags: "1",
        probability: "90%",
        status: "1"
    },
    {
        id: 3,
        deal_name: "Enterprise Plan",
        stage: "Contact Made",
        deal_value: "$10,36,390",
        owner_name: "John Smith",
        owner_img: "assets/img/profiles/avatar-06.jpg",
        tags: "2",
        probability: "80%",
        status: "3"
    },
    {
        id: 4,
        deal_name: "BrightWorks",
        stage: "Presentation",
        deal_value: "$16,11,420",
        owner_name: "Sophia Parker",
        owner_img: "assets/img/profiles/avatar-12.jpg",
        tags: "0",
        probability: "72%",
        status: "3"
    },
    {
        id: 5,
        deal_name: "Sales Pipeline",
        stage: "Proposal Made",
        deal_value: "$90,59,472",
        owner_name: "Emma Reynolds",
        owner_img: "assets/img/profiles/avatar-18.jpg",
        tags: "3",
        probability: "60%",
        status: "2"
    }
],

};



 