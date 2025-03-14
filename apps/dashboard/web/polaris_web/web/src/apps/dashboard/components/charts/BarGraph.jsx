import React, { useEffect } from 'react'
import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";
import observeFunc from "../../pages/observe/transform"

function BarGraph({defaultChartOptions, backgroundColor, height, title, data, xAxisTitle,barWidth, yAxisTitle, barGap, showGridLines, showYAxis}) {

    const chartComponentRef = useRef(null)
    let xCategories = []
    let seriesData = []
    data.forEach((x) => {
        xCategories.push(x.text)
        seriesData.push({
            y: x.value,
            color: x.color,
            name: x.text
        })
    })
    const chartOptions = {
        chart: {
            type: 'column',
            backgroundColor: backgroundColor || "white",
            height: height || '300px',
        },
        exporting: {
            enabled: false
        },
        title: {
            text: title || '',
        },
        xAxis: {
            categories: xCategories,
            title: {
                text: xAxisTitle || '',
            },
        },
        yAxis: [
            {
                title: {
                    text: yAxisTitle || '',
                },
                visible: showYAxis,
                gridLineWidth: showGridLines ? 1 : 0,
                min: 0,
            }
        ],
        tooltip: {
            backgroundColor: {
                linearGradient: [0, 0, 0, 60],
                stops: [
                    [0, '#FFFFFF'],
                    [1, '#E0E0E0']
                ]
            },
            headerFormat: '',
            formatter: function() {
                return `<b>${this.point.name} </b> ${observeFunc.formatNumberWithCommas(this.point.y)}`;
            },
            borderWidth: 1,
            borderColor: '#AAA'
        },
        plotOptions: {
            series: {
                pointWidth: barWidth || 20, 
                pointPadding: barGap || 5,      
                groupPadding: 0,            
                borderWidth: 0,
            },
        },
        credits:{
            enabled: false,
        },
        series:[{
            data: seriesData,
            colorByPoint: true,
            states: {
                inactive: {
                    opacity: 1
                }
            }
        }],
        ...defaultChartOptions,
    };

    useEffect(() => {
        const mediaQueryList = window.matchMedia('print')

        const handlePrint = (e) => {
            const chart = chartComponentRef.current.chart
            if (e.matches) {
                chart.setSize(600, 400, false)
            } else {
                chart.setSize(null, null, false)
            }
        }

        mediaQueryList.addEventListener('change', handlePrint)
        return () => mediaQueryList.removeEventListener('change', handlePrint)
    }, [])

    return (
        <HighchartsReact 
            highcharts={Highcharts} 
            options={chartOptions} 
            ref={chartComponentRef}
        />
    )
}

export default BarGraph