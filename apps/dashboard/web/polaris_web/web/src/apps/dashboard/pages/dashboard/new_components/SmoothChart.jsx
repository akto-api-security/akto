import React from 'react';
import HighchartsReact from 'highcharts-react-official';
import Highcharts from 'highcharts';

function SmoothAreaChart({
    tickPositions,
    color,
    height = "80",
    width = "150",
    labels = [],
    enableHover = false,
}) {
    const safe = tickPositions && tickPositions.length ? tickPositions : [0];
    const min = Math.min(...safe);
    const max = Math.max(...safe);
    const pad = (max - min) * 0.2 || 1;

    const chartOptions = {
        chart: {
            type: 'areaspline',
            height,
            width,
            backgroundColor: 'transparent',
            margin: color ? [4, 0, 2, 0] : undefined,
            spacing: color ? [0, 0, 0, 0] : undefined,
            animation: false,
        },
        credits: { enabled: false },
        title: { text: '' },
        xAxis: { visible: false },
        yAxis: color
            ? { visible: false, min: min - pad, max: max + pad }
            : {
                title: { text: null },
                labels: { style: { color: '#999', fontSize: '12px' }, align: 'right' },
                gridLineWidth: 0,
                opposite: true,
                endOnTick: false,
                visible: false,
            },
        tooltip: enableHover
            ? {
                enabled: true,
                outside: true,
                backgroundColor: 'white',
                borderColor: '#DFE3E8',
                borderRadius: 6,
                style: { fontSize: '11px' },
                formatter: function () {
                    const label = labels[this.point.index];
                    return label ? `<b>${label}:</b> ${this.y}` : `<b>${this.y}</b>`;
                },
            }
            : { enabled: false },
        legend: { enabled: false },
        plotOptions: {
            areaspline: {
                fillColor: color
                    ? {
                        linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                        stops: [
                            [0, Highcharts.color(color).setOpacity(0.25).get('rgba')],
                            [1, Highcharts.color(color).setOpacity(0).get('rgba')],
                        ],
                    }
                    : {
                        linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                        stops: [
                            [0, 'rgba(150,150,255,0.8)'],
                            [1, 'rgba(150,150,255,0.1)'],
                        ],
                    },
                lineWidth: color ? 2 : 0,
                marker: { enabled: false },
                states: { hover: { enabled: enableHover, lineWidth: 2 } },
                enableMouseTracking: enableHover,
                stickyTracking: false,
            },
        },
        exporting: { enabled: false },
        series: color
            ? [{ data: safe, color }]
            : [
                {
                    type: 'areaspline',
                    data: tickPositions,
                    fillColor: {
                        linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                        stops: [
                            [0, 'rgba(150,150,255,0.8)'],
                            [1, 'rgba(150,150,255,0.1)'],
                        ],
                    },
                    lineWidth: 0,
                    zIndex: 0,
                },
                {
                    type: 'areaspline',
                    data: tickPositions,
                    fillColor: 'transparent',
                    lineWidth: 2,
                    lineColor: 'rgba(150,150,255,1)',
                    zIndex: 1,
                },
            ],
    };

    return <HighchartsReact highcharts={Highcharts} options={chartOptions} />;
}

export default SmoothAreaChart;
