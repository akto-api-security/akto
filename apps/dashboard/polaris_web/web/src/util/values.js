const today = new Date(new Date().setHours(0, 0, 0, 0));
const yesterday = new Date(
    new Date(new Date().setDate(today.getDate() - 1)).setHours(0, 0, 0, 0)
);
const ranges = [
    {
        title: "Today",
        alias: "today",
        period: {
            since: today,
            until: today,
        },
    },
    {
        title: "Yesterday",
        alias: "yesterday",
        period: {
            since: yesterday,
            until: yesterday,
        },
    },
    {
        title: "Last 7 days",
        alias: "last7days",
        period: {
            since: new Date(
                new Date(new Date().setDate(today.getDate() - 7)).setHours(
                    0,
                    0,
                    0,
                    0
                )
            ),
            until: yesterday,
        },
    },
    {
        title: "Last 2 months",
        alias: "recencyPeriod",
        period:{
            since: new Date(
                new Date(new Date().setDate(today.getDate() - 60)).setHours(
                    0,
                    0,
                    0,
                    0
                )
            ),
            until: today,
        }
    }
];

export default { today, yesterday, ranges };