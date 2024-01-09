import React from 'react'
import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";

function ConcentricCirclesChart({data,title,size,subtitle}) {

    const chartComponentRef = useRef(null)
    let seriesData = []
    let startSize = size
    Object.keys(data).forEach((ele)=>{
        let obj = {
            innerSize: startSize - 27,
            size: startSize,
            data:[{name: ele, y: data[ele].text , color: data[ele].color}, {name: '', y: title - data[ele].text, color: '#F6F6F7'}]
        }
        startSize -= 32
        seriesData.push(obj)
    })

    const chartOptions = {
        chart:{
            type: 'pie',
            height: size + 10,
            width: size,
            className: 'pie-chart',
            margin: '10'
        },
        tooltip:{
            enabled: false
        },
        credits:{
            enabled: false,
        },
        title:{
            text: title,
            y: size*0.6,
        },
        subtitle:{
            text: subtitle,
            y: size * 0.4,
            style:{
                color: "#202223"
            }
        },
        series: seriesData,
        plotOptions: {
            pie: {
                allowPointSelect: false,
                cursor: 'pointer',
                dataLabels: {
                    enabled: false,
                }
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

export default ConcentricCirclesChart