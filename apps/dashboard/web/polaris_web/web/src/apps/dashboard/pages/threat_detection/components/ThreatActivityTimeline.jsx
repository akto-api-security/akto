import { HighchartsReact } from "highcharts-react-official";
import Highcharts from "highcharts";
import InfoCard from "../../dashboard/new_components/InfoCard";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { Spinner } from "@shopify/polaris";
import api from "../api";


export const ThreatActivityTimeline = ({ startTimestamp, endTimestamp }) => {
    const [seriesData, setSeriesData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [sortedTimelines, setSortedTimelines] = useState([]);

    const COLORMAP = {
        0: 'rgb(133, 62, 232)',
        1: 'rgb(133, 62, 232, 0.9)',
        2: 'rgb(133, 62, 232, 0.8)',
        3: 'rgb(133, 62, 232, 0.7)',
        4: 'rgb(133, 62, 232, 0.6)',
        5: 'rgba(133, 62, 232, 0.5)',
        6: 'rgba(133, 62, 232, 0.4)',
        7: 'rgba(133, 62, 232, 0.3)',
        8: 'rgba(133, 62, 232, 0.2)',
        9: 'rgba(133, 62, 232, 0.1)',
    }

    const fetchThreatActivityTimeline = async () => {
        const response = await api.getThreatActivityTimeline(1741564800, endTimestamp);
        const sortedTimelines = response.threatActivityTimelines.sort((a, b) => a.ts - b.ts);
        setSortedTimelines(sortedTimelines);
        const distinctSubCategories = [...new Set(response.threatActivityTimelines.flatMap(item => item.subCategoryWiseData.map(subItem => subItem.subcategory)))];
        console.log({ response });
        const series = distinctSubCategories.map((subCategory, index) => ({
            color: COLORMAP[index],
            name: subCategory,
            data: sortedTimelines.map(item => {
                const found = item.subCategoryWiseData.find(
                    subItem => subItem.subcategory === subCategory
                );
                return found ? found.activityCount : 0;
            })
        }));

        setSeriesData(series);
        setLoading(false);
    }


    useEffect(() => {
        fetchThreatActivityTimeline();
    }, [startTimestamp, endTimestamp]);

    const chartOptions = {
        chart: {
            type: "column"
        },
        credits: {
            enabled: false
        },
        title: {
            text: undefined,
        },
        exporting: {
            enabled: false
        },
        xAxis: {
            title: {
                text: "Timeline"
            },
            categories: sortedTimelines.map(item => dayjs(item.ts*1000).format('D MMM YYYY'))
        },
        yAxis: {
            title: {
                text: "# of APIs"
            }
        },
        plotOptions: {
            column: {
                stacking: 'normal'
            }
        },
        legend: {
            enabled: false
        },
        series: seriesData,
    }

    if (loading) {
        return<Spinner />;
    }

    return (
        <InfoCard
            title={"Threat Activity Timeline"}
            titleToolTip={"Threat activity by sub-category over time. Maximum of one week is displayed."}
            component={<HighchartsReact highcharts={Highcharts} options={chartOptions} />}
        />
    )
}