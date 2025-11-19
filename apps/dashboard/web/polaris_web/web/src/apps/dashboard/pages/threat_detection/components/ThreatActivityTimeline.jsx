import { HighchartsReact } from "highcharts-react-official";
import Highcharts from "highcharts";
import InfoCard from "../../dashboard/new_components/InfoCard";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { Spinner } from "@shopify/polaris";
import api from "../api";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";


const ThreatActivityTimeline = ({ startTimestamp, endTimestamp, onSubCategoryClick }) => {
    const [seriesData, setSeriesData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [sortedTimelines, setSortedTimelines] = useState([]);

    const COLORMAP = {
        0: 'rgb(133, 62, 232)',
        1: 'rgb(133, 62, 232, 0.8)',
        2: 'rgb(133, 62, 232, 0.6)',
        3: 'rgb(133, 62, 232, 0.4)',
        4: 'rgb(133, 62, 232, 0.2)',
    }

    const fetchThreatActivityTimeline = async () => {
        const response = await api.getThreatActivityTimeline(startTimestamp, endTimestamp);
        const sortedTimelines = response.threatActivityTimelines.sort((a, b) => a.ts - b.ts);
        setSortedTimelines(sortedTimelines);
        const distinctSubCategories = [...new Set(response.threatActivityTimelines.flatMap(item => item.subCategoryWiseData.map(subItem => subItem.subcategory)))];
        const series = distinctSubCategories.map((subCategory, index) => ({
            color: COLORMAP[index % 5],
            name: subCategory,
            data: sortedTimelines.map(item => {
                const found = item.subCategoryWiseData.find(
                    subItem => subItem.subcategory === subCategory
                );
                return found ? found.activityCount : 0;
            }),
            events: {
                click: (e) => {
                    onSubCategoryClick(subCategory);
                }
            }
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
                text: `# of ${mapLabel("APIs", getDashboardCategory())}`
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
            title={`${mapLabel("Threat", getDashboardCategory())} Activity Timeline`}
            titleToolTip={`${mapLabel("Threat", getDashboardCategory())} activity by sub-category over time. Maximum of one week is displayed.`}
            component={<HighchartsReact highcharts={Highcharts} options={chartOptions} />}
        />
    )
}

export default ThreatActivityTimeline;