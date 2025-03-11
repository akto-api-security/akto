import { HighchartsReact } from "highcharts-react-official";
import Highcharts from "highcharts";
import InfoCard from "../../dashboard/new_components/InfoCard";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { Spinner } from "@shopify/polaris";

const THREAT_ACTIVITY_TIMELINE_DATA = {
    "apis": null,
    "categoryCounts": null,
    "dailyThreatActorsCount": "SUCCESS",
    "endTimestamp": 0,
    "skip": 0,
    "sort": null,
    "startTimestamp": 0,
    "total": 0,
    "threatActivityTimeline": [
      {
        "ts": 1741651200,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 15
          },
          {
            "subcategory": "SSRF", 
            "activityCount": 12
          },
          {
            "subcategory": "IDOR",
            "activityCount": 8
          },
          {
            "subcategory": "SSTI",
            "activityCount": 5
          }
        ]
      },
      {
        "ts": 1741564800,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 18
          },
          {
            "subcategory": "SSRF",
            "activityCount": 10
          },
          {
            "subcategory": "IDOR", 
            "activityCount": 12
          },
          {
            "subcategory": "SSTI",
            "activityCount": 7
          }
        ]
      },
      {
        "ts": 1741478400,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 9
          },
          {
            "subcategory": "SSRF",
            "activityCount": 15
          },
          {
            "subcategory": "IDOR",
            "activityCount": 6
          },
          {
            "subcategory": "SSTI",
            "activityCount": 11
          }
        ]
      },
      {
        "ts": 1741392000,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 13
          },
          {
            "subcategory": "SSRF",
            "activityCount": 8
          },
          {
            "subcategory": "IDOR",
            "activityCount": 16
          },
          {
            "subcategory": "SSTI",
            "activityCount": 4
          }
        ]
      },
      {
        "ts": 1741305600,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 7
          },
          {
            "subcategory": "SSRF",
            "activityCount": 19
          },
          {
            "subcategory": "IDOR",
            "activityCount": 11
          },
          {
            "subcategory": "SSTI",
            "activityCount": 14
          }
        ]
      },
      {
        "ts": 1741219200,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 16
          },
          {
            "subcategory": "SSRF",
            "activityCount": 6
          },
          {
            "subcategory": "IDOR",
            "activityCount": 9
          },
          {
            "subcategory": "SSTI",
            "activityCount": 17
          }
        ]
      },
      {
        "ts": 1741132800,
        subCategoryWiseData: [
          {
            "subcategory": "BOLA",
            "activityCount": 11
          },
          {
            "subcategory": "SSRF",
            "activityCount": 14
          },
          {
            "subcategory": "IDOR",
            "activityCount": 19
          },
          {
            "subcategory": "SSTI",
            "activityCount": 8
          }
        ]
      }
    ]
  }

export const ThreatActivityTimeline = ({ startTimestamp, endTimestamp }) => {
    const [seriesData, setSeriesData] = useState([]);
    const [loading, setLoading] = useState(false);

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

    const getThreatActivityTimeline = async () => {
        setLoading(true);
        const response = THREAT_ACTIVITY_TIMELINE_DATA;
        
        const distinctSubCategories = [...new Set(response.threatActivityTimeline.flatMap(item => item.subCategoryWiseData.map(subItem => subItem.subcategory)))];
        
        const series = distinctSubCategories.map((subCategory, index) => ({
            color: COLORMAP[index],
            name: subCategory,
            data: response.threatActivityTimeline.map(item => item.subCategoryWiseData.find(subItem => subItem.subcategory === subCategory).activityCount),
        }));

        setSeriesData(series);
        setLoading(false);
    }


    useEffect(() => {
        getThreatActivityTimeline();
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
            categories: THREAT_ACTIVITY_TIMELINE_DATA.threatActivityTimeline.map(item => dayjs(item.ts*1000).format('ddd'))
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
            titleToolTip={"Threat Activity Timeline"}
            component={<HighchartsReact highcharts={Highcharts} options={chartOptions} />}
        />
    )
}