const today = new Date(new Date().setHours(0, 0, 0, 0));
const todayDayEnd = new Date(new Date().setHours(23, 59, 59, 999));
const yesterday = new Date(
    new Date(new Date().setDate(today.getDate() - 1)).setHours(0, 0, 0, 0)
);
const yesterdayDayEnd = new Date(
    new Date(new Date().setDate(today.getDate() - 1)).setHours(23, 59, 59, 999)
);

const ranges = [
    {
        title: "Today",
        alias: "today",
        period: {
            since: today,
            until: todayDayEnd,
        },
    },
    {
        title: "Yesterday",
        alias: "yesterday",
        period: {
            since: yesterday,
            until: yesterdayDayEnd,
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
            until: todayDayEnd,
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
            until: todayDayEnd,
        }
    }
];

export default { today, yesterday, ranges, yesterdayDayEnd, todayDayEnd };