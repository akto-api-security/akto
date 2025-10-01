import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import { useEffect, useRef, useState } from "react";
import func from "../../../../util/func";
import SpinnerCentered from "../progress/SpinnerCentered";

function LineChart(props) {

    const { type, height, backgroundColor, data, graphPointClick, tooltipFormatter, yAxisTitle, title, text, defaultChartOptions, areaFillHex, color, width, noGap, showGridLines, exportingDisabled } = props;
    const chartComponentRef = useRef(null)

    const fillColor = {
        linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
        stops: [
            [0, color],
            [1, '#000000'],
        ],
    };

    const coreChartOptions = {
        chart: {
            type: type,
            height: (height) + 'px',
            spacing: [5, 0, 0, 0],
            backgroundColor: backgroundColor
        },
        credits: {
            enabled: false,
        },
        title: {
            text: title,
            align: 'left',
            margin: 20
        },
        tooltip:{
            shared: false,
        },
        exporting: {
            enabled: !exportingDisabled
        },
        plotOptions: {
            column: {
                dataLabels: {
                    enabled: true
                }
            },
            series: {
                minPointLength: noGap ? 0 : 5,
                pointWidth: width,
                cursor: 'pointer',
                point: {
                    events: {
                        click: () => {
                        }
                    }
                }
            }
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
                    text: yAxisTitle,
                },
                visible: text,
                gridLineWidth: showGridLines ? 1 : 0,
                min: 1,
            }
        ],
        time: {
            useUTC: false
        },
        ...defaultChartOptions
    }

    function processChartData(data) {
        return  data.map((x, i) => {
            return {
                data: x.data,
                color: x.color,
                name: x.name,
                fillColor: areaFillHex ? fillColor : {},
                marker: {
                    enabled: false
                },
                yAxis: i==1 ? 1 : 0,
                lineWidth: i==1 ? 1: 3

            }
        })
    }

    const [chartOptions, setChartOptions] = useState({...coreChartOptions, series: []});

    useEffect(() => {
        setChartOptions((prev) => {
            let tmp = processChartData(data);
            if (func.deepComparison(prev.series, tmp)) {
                return prev;
            }
            prev.series = tmp;
            prev.yAxis = tmp.map((x, i) => {
                return {
                    title: {
                        text: x.name,
                        color: x.color
                    },
                    visible: x.name,
                    gridLineWidth: 1,
                    min: 1,
                    opposite: i==1,
                }
            })
            prev.plotOptions.series.point.events.click = graphPointClick
            prev.tooltip.formatter = tooltipFormatter
            return {...prev};
        })
    }, [data])

    return (
        data.length > 0 ? <HighchartsReact
        key={"chart"}
        highcharts={Highcharts}
        options={chartOptions}
        ref={chartComponentRef}
    /> : <SpinnerCentered/>
    )
}

export default LineChart;