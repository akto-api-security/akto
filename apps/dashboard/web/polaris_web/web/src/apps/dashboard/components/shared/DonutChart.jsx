import React from 'react'
import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";
import { useNavigate } from "react-router-dom"


function DonutChart({data, title, size,type}) {
    const chartComponentRef = useRef(null)
    const navigate = useNavigate()


    let seriesData = []
    if(data && Object.keys(data).length > 0){
        seriesData = Object.keys(data).map((ele)=>{
            return{
                name: ele,
                y: data[ele].text,
                color: data[ele].color,
            }
        })
    }

    const chartOptions = {
        chart:{
            type: 'pie',
            height: size + 10,
            width: size,
            className: 'pie-chart',
            margin: '10'
        },
        credits:{
            enabled: false,
        },
        title:{
            text: title,
            y: size*0.4,
        },
        tooltip: {
            backgroundColor: {
                linearGradient: [0, 0, 0, 60],
                stops: [
                    [0, '#FFFFFF'],
                    [1, '#E0E0E0']
                ]
            },
            headerFormat: '',
            pointFormat: '<b>{point.name} </b> {point.y}',
            borderWidth: 1,
            borderColor: '#AAA'
        },
        plotOptions: {
            pie: {
              size: (size+'px'),
              innerSize: '60%',
              dataLabels: {
                enabled: false
              }
            },
            series: {
                point: {
                    events: {

                        click: (event) => {
                            const { point } = event;
                            navigate(`/dashboard/observe/sensitive/${point.name}?filter=${type.toLowerCase()}`);
                          }
                    }
                }

            }
        },
        series:[{
            data: seriesData,
            colorByPoint: true,
            states: {
                inactive: {
                    opacity: 1
                }
            }
        }]
    }

    return (
        <HighchartsReact 
            highcharts={Highcharts} 
            options={chartOptions} 
            ref={chartComponentRef}
        />
    )
}

export default DonutChart