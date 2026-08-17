import func from "./func";

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
                new Date(new Date().setDate(today.getDate() - 6)).setHours(
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
        title: "Last 1 month",
        alias: "last1month",
        period: {
            since: new Date(
                new Date(new Date().setDate(today.getDate() - 30)).setHours(
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
    },
    {
        title: 'Last 1 year',
        alias: "lastYear",
        period:{
            since: new Date(
                new Date(new Date().setDate(today.getDate() - 365)).setHours(
                    0,
                    0,
                    0,
                    0
                )
            ),
            until: todayDayEnd,
        }
    },
    {
        title: 'All time',
        alias: "allTime",
        period:{
            since: new Date(1000),
            until: new Date(new Date().setFullYear(today.getFullYear() + 1)),
        }
    }
];

// Look up a preset by alias. Call sites must use this rather than ranges[i] —
// inserting a preset shifts every index and silently changes page defaults.
// The demo account always opens on All time: its seeded data spans years, so the
// normal default would land most pages on an empty range. Doing it here covers
// every date picker at once instead of a per-page override.
const getRange = (alias) => {
    const wanted = func.isDemoAccount() ? "allTime" : alias;
    return ranges.find((r) => r.alias === wanted) || ranges[0];
};

const skipList = ["GENERIC", "TRUE", "FALSE","INTEGER_32", "INTEGER_64", "NULL", "OTHER", "DICT", "FLOAT"]
const DISABLED_AUTO_ACCOUNT_REFRESH = [1747820267,1731351930,1736798101]

export default { today, yesterday, ranges, getRange, yesterdayDayEnd, todayDayEnd , skipList, DISABLED_AUTO_ACCOUNT_REFRESH};