import React from 'react'
import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useRef } from "react";
import { useNavigate } from "react-router-dom"
import PersistStore from '../../../main/PersistStore';
import observeFunc from "../../pages/observe/transform"


function DonutChart({data, title, size,type,navUrl, isRequest, pieInnerSize, subtitle, navUrlBuilder}) {
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
            margin: '10',
            events: subtitle ? {
                render: function() {
                    const chart = this;
                    const centerX = chart.plotLeft + (chart.plotWidth / 2);
                    const centerY = chart.plotTop + (chart.plotHeight / 2);
                    
                    // Remove old labels if they exist
                    if (chart.titleLabel) {
                        chart.titleLabel.destroy();
                    }
                    if (chart.subtitleLabel) {
                        chart.subtitleLabel.destroy();
                    }
                    
                    // Add title
                    chart.titleLabel = chart.renderer.text(
                        title,
                        centerX,
                        centerY - 5
                    )
                    .css({
                        fontSize: '20px',
                        fontWeight: '400',
                        textAlign: 'center'
                    })
                    .attr({
                        'text-anchor': 'middle'
                    })
                    .add();
                    
                    // Add subtitle
                    chart.subtitleLabel = chart.renderer.text(
                        subtitle,
                        centerX,
                        centerY + 15
                    )
                    .css({
                        fontSize: '12px',
                        color: '#666',
                        textAlign: 'center'
                    })
                    .attr({
                        'text-anchor': 'middle'
                    })
                    .add();
                }
            } : undefined
        },
        credits:{
            enabled: false,
        },
        title: subtitle ? { text: null } : {
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
                            if (navUrlBuilder) {
                                const builtUrl = navUrlBuilder(navUrl, point.filterValue)
                                if (builtUrl) {
                                    window.open(builtUrl, '_blank', 'noopener,noreferrer')
                                }
                            }
                            else if(navUrl && navUrl ==='/dashboard/observe/sensitive/'){
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
                            else if( navUrl && navUrl ==='/dashboard/issues'){
                                const filterUrl = '/dashboard/issues/#open'
                                const filterUrl1 = '/dashboard/issues//#open'
                                let updatedFiltersMap = { ...filtersMap };
                                updatedFiltersMap[filterUrl] = {}
                                updatedFiltersMap[filterUrl1] = {}
                                let key = "issueCategory"
                                if(point.filterKey.toUpperCase() === "CRITICAL")
                                  key = "severity"
                                else if(point.filterKey.toUpperCase() === "HIGH")
                                  key = "severity"
                                else if(point.filterKey.toUpperCase() === "MEDIUM")
                                  key = "severity"
                                else if(point.filterKey.toUpperCase() === "LOW")
                                  key = "severity"
                                const filterObj = [{
                                    key: key,
                                    label: point.filterKey,
                                    value: [point.filterKey]
                                }
                               ]

                                updatedFiltersMap[filterUrl]['filters'] = filterObj;
                                updatedFiltersMap[filterUrl]['sort'] = [];
                                updatedFiltersMap[filterUrl1]['filters'] = filterObj;
                                updatedFiltersMap[filterUrl1]['sort'] = [];
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