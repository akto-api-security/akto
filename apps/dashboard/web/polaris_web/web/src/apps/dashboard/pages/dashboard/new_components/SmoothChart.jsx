import React from 'react';
import HighchartsReact from 'highcharts-react-official';
import Highcharts from 'highcharts';

function SmoothAreaChart({tickPositions}) {
    const chartOptions = {
        chart: {
            type: 'areaspline',
            height: "80",
            width: "150"
        },
        credits:{
            enabled: false,
        },
        title: {
            text: ''
        },
        xAxis: {
            visible: false
        },
        yAxis: {
            title: {
                text: null
            },
            labels: {
                style: {
                    color: '#999',
                    fontSize: '12px'
                },
                align: 'right'
            },
            gridLineWidth: 0,
            opposite: true,
            endOnTick: false,
            visible: false
        },
        tooltip: {
            enabled: false
        },
        legend: {
            enabled: false
        },
        plotOptions: {
            areaspline: {
                marker: {
                    enabled: false
                },
                states: {
                    hover: {
                        enabled: false
                    }
                },
                enableMouseTracking: false,
                stickyTracking: false
            }
        },
        exporting: {
            enabled: false
        },
        series: [
            {
                type: 'areaspline',
                data: tickPositions,
                fillColor: {
                    linearGradient: {
                        x1: 0,
                        y1: 0,
                        x2: 0,
                        y2: 1
                    },
                    stops: [
                        [0, 'rgba(150,150,255,0.8)'],
                        [1, 'rgba(150,150,255,0.1)']
                    ]
                },
                lineWidth: 0,
                zIndex: 0
            },
            {
                type: 'areaspline',
                data: tickPositions,
                fillColor: 'transparent',
                lineWidth: 2,
                lineColor: 'rgba(150,150,255,1)',
                zIndex: 1
            }
        ]
    };

    return (
        <HighchartsReact
            highcharts={Highcharts}
            options={chartOptions}
        />
    );
}

export default SmoothAreaChart;
