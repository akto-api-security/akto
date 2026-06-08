import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";
require("highcharts/modules/exporting")(Highcharts);
require("highcharts/modules/export-data.src")(Highcharts);
require("highcharts/modules/accessibility")(Highcharts);
require("highcharts/modules/boost")(Highcharts);
require("highcharts/modules/mouse-wheel-zoom")(Highcharts);


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

    const dataForChart = data.map((x) => {
        return {
            data: x['data'],
            color: x['color'],
            name: x['name'],
            fillColor: props.areaFillHex ? fillColor : 'none',
            marker: { enabled: false },
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
            animation: false,
            spacingTop: 20,
            spacingLeft: 16,
            spacingRight: 16,
            spacingBottom: 16,
            zooming: {
                type: 'x',
                mouseWheel: {
                    enabled: true,
                    sensitivity: 1.5,
                },
                resetButton: { position: { align: 'right' } },
            },
            panning: { enabled: true, type: 'x' },
            panKey: 'shift',
        },
        credits:{
            enabled: false,
        },
        title: {
            text: title,
            align: 'left',
            margin: 12,
            style: { fontSize: '16px', fontWeight: '600' },
        },
        subtitle: {
            text: subtitle,
            align: 'left',
            style: { fontSize: '12px' },
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
                gridLineWidth: 1,
            },
            ...inputMetrics.map(() => ({
                title: {
                    text: '',
                },
                visible: true,
                opposite: true,
            })),
        ],
        ...defaultChartOptions,
    };
    return (
        <div onWheel={(e) => e.stopPropagation()}>
            <HighchartsReact
                highcharts={Highcharts}
                options={chartOptions}
                ref={chartComponentRef}
            />
        </div>
    )
}

export default GraphMetric