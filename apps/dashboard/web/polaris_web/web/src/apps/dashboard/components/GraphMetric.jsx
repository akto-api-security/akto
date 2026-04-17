import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";
require("highcharts/modules/exporting")(Highcharts);
require("highcharts/modules/export-data.src")(Highcharts);
require("highcharts/modules/accessibility")(Highcharts);


function GraphMetric(props) {

    const { height, backgroundColor, data, inputMetrics, title, text, defaultChartOptions, subtitle, timezoneOffsetMinutes } = props;
    const chartComponentRef = useRef(null)

    const fillColor = {
        linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
        stops: [
            [0, props.color],
            [1, '#000000'],
        ],
    };

    const dataForChart = data.map((x, idx) => {
        return {
            data: x['data'],
            color: x['color'],
            name: x['name'],
            fillColor: props.areaFillHex ? fillColor : {},
            yAxis: 0,
        };
    });

    const series = [
        ...dataForChart,
        ...(inputMetrics.length > 0 ? inputMetrics.map((x, i) => {
            return {
                data: x.data,
                color: '#FF4DCA',
                name: x.name,
                marker: {
                    enabled: false,
                    symbol: 'circle',
                },
                fillColor: {
                    linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                    stops: [
                        [0, '#000000'],
                        [1, '#000000'],
                    ],
                },
                yAxis: i + 1,
            };
        }) : []),
    ];

    const chartOptions = {
        chart: {
            type: 'spline',
            height: `${height}px`,
            backgroundColor,
        },
        credits:{
            enabled: false,
        },
        title: {
            text: title,
            align: 'left',
            margin: 20
        },
        subtitle: {
            text: subtitle,
            align: 'left'
        },
        tooltip: {
            shared: true,
        },
        series,
        time: {
            timezoneOffset: timezoneOffsetMinutes != null ? -timezoneOffsetMinutes : new Date().getTimezoneOffset(),
        },
        xAxis: {
            type: 'datetime',
            dateTimeLabelFormats: {
                day: '%b %e',
                month: '%b',
            },
            title: {
                text: 'Date',
            },
            visible: text,
            gridLineWidth: 0,
        },
        yAxis: [
            {
                title: {
                    text: title,
                },
                visible: text,
                gridLineWidth: 0,
                min: 0,
            },
            ...inputMetrics.map(() => ({
                title: {
                    text: '',
                },
                visible: true,
                opposite: true,
                min: 0,
            })),
        ],
        ...defaultChartOptions,
    };
    return (
        <HighchartsReact 
            highcharts={Highcharts} 
            options={chartOptions} 
            ref={chartComponentRef}
        />
    )
}

export default GraphMetric