import React from 'react'
import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";
import { useNavigate } from "react-router-dom"
import PersistStore from '../../../main/PersistStore';
import observeFunc from "../../pages/observe/transform"


function DonutChart({data, title, size,type,navUrl, isRequest, pieInnerSize}) {
    const chartComponentRef = useRef(null)
    const navigate = useNavigate()
    const filtersMap = PersistStore(state => state.filtersMap)
    const setFiltersMap = PersistStore(state => state.setFiltersMap)


    let seriesData = []
    if(data && Object.keys(data).length > 0){
        seriesData = Object.keys(data).map((ele)=>{
            return {...data[ele], y: data[ele].text, name: ele }
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
            useHTML: true,
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
            pie: {
              size: (size+'px'),
              innerSize: pieInnerSize ? pieInnerSize : '60%',
              dataLabels: {
                enabled: false
              }
            },
            series: {
                point: {
                    events: {

                        click: (event) => {
                            const { point } = event;
                            if(navUrl && navUrl ==='/dashboard/observe/sensitive/'){
                                if(isRequest){
                                    const filterUrl = `${navUrl}${point.name}`
                                    let updatedFiltersMap = { ...filtersMap };
                                    updatedFiltersMap[filterUrl] = {}
                                    const filterObj = [{key: "isRequest", label: "In request", value: [true]}]
                                    updatedFiltersMap[filterUrl]['filters'] = filterObj;
                                    updatedFiltersMap[filterUrl]['sort'] = [];
                                    setFiltersMap(updatedFiltersMap)
                                }
                                navigate(`${navUrl}${point.name}`);
                            }
                            else if( navUrl && navUrl==='/dashboard/issues/'){
                                const filterUrl = '/dashboard/issues'
                                let updatedFiltersMap = { ...filtersMap };
                                updatedFiltersMap[filterUrl] = {}
                                  
                                const filterObj = [{
                                    key: "issueCategory",
                                    label: point.filterKey,
                                    value: [point.filterKey]
                                }
                               ]

                                updatedFiltersMap[filterUrl]['filters'] = filterObj;
                                updatedFiltersMap[filterUrl]['sort'] = [];
                                setFiltersMap(updatedFiltersMap)
                                navigate(`${navUrl}`);
                            }
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