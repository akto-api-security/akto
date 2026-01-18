import React from 'react'
import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";

function SemiCircleProgress({progress, size, height, width, title, color, centerText, backgroundColor, subtitle}) {

    const chartComponentRef = useRef(null)
    const seriesData = [{
        innerSize: '80%',
        name: '',
        data: [
            {name: 'completed', color: color || "#6200EA", y: progress},
            {name: '', y: 100 - progress, color: backgroundColor || '#F6F6F7'}
        ]
    }]
    const chartOptions = {
        chart:{
            type: 'pie',
            height: height,
            width: width,
            className: 'pie-chart',
            margin: -50,
            backgroundColor: 'transparent',
        },
        tooltip:{
            enabled: false
        },
        credits:{
            enabled: false,
        },
        title: subtitle ? {
            text: subtitle,
            y: 0.3*size,
            style:{
                fontSize: '12px',
                color: '#667085',
                fontWeight: 'normal'
            }
        } : {
            text: progress + '%',
            y: 0.4*size,
            style:{
                fontWeight: 'medium',
                color: "#344054",
                fontSize: '14px'
            }
        },
        subtitle: centerText ? {
            text: centerText,
            y: 0.5*size,
            style:{
                fontSize: '16px',
                fontWeight: '600',
                color: "#344054"
            }
        } : undefined,
        series: seriesData,
        exporting: {
            enabled: false
        },
        plotOptions: {
            pie: {
                size: size,
                allowPointSelect: false,
                cursor: 'pointer',
                dataLabels: {
                    enabled: false,
                },
                startAngle: -90,
                endAngle: 90,
                center: ['50%', '60%'],
            },
            series: {
                states: {
                    hover: {
                        enabled: false
                    },
                    inactive: {
                        opacity: 1
                    }
                }
            }
        }
    }
    return (
        <HighchartsReact 
            highcharts={Highcharts} 
            options={chartOptions} 
            ref={chartComponentRef}
        />
    )
}

export default SemiCircleProgress