import HighchartsReact from "highcharts-react-official"
import Highcharts from "highcharts"
import HighchartsTreemap from "highcharts/modules/treemap"
import { useRef } from "react"

// Initialize the treemap module
if (typeof Highcharts === 'object') {
    HighchartsTreemap(Highcharts)
}

function TreeMapChart(props) {
    const { height = 300, data = [], exportingDisabled = true } = props
    const chartComponentRef = useRef(null)

    const chartOptions = {
        chart: {
            type: 'treemap',
            height: height + 'px',
            backgroundColor: 'transparent'
        },
        credits: {
            enabled: false
        },
        title: {
            text: ''
        },
        exporting: {
            enabled: !exportingDisabled
        },
        legend: {
            enabled: false
        },
        tooltip: {
            enabled: false
        },
        plotOptions: {
            treemap: {
                layoutAlgorithm: 'squarified',
                borderWidth: 2,
                borderColor: '#ffffff',
                cursor: 'pointer',
                states: {
                    hover: {
                        enabled: true,
                        brightness: 0.05,
                        color: '#DCC7FF'
                    }
                },
                dataLabels: {
                    enabled: true,
                    useHTML: true,
                    align: 'left',
                    verticalAlign: 'bottom',
                    x: 8,
                    y: -8,
                    overflow: 'allow',
                    crop: false,
                    formatter: function() {
                        const point = this.point
                        const iconUrl = point.icon || ''
                        const width = point.shapeArgs?.width || 100
                        const height = point.shapeArgs?.height || 100

                        // Use horizontal layout if box height is too small
                        const isSmallHeight = height < 80
                        const flexDirection = isSmallHeight ? 'row' : 'column'
                        const alignItems = isSmallHeight ? 'center' : 'flex-start'

                        return `<div style="display: flex; flex-direction: ${flexDirection}; align-items: ${alignItems}; gap: 6px; max-width: ${width - 16}px; max-height: ${height - 16}px; overflow: hidden;">
                            ${iconUrl ? `<img src="${iconUrl}" alt="${point.name}" style="width: 20px; height: 20px; object-fit: contain; display: block; flex-shrink: 0;" />` : ''}
                            <div style="font-weight: 500; font-size: 14px; color: #202223; line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%;">${point.name}</div>
                        </div>`
                    },
                    style: {
                        textOutline: 'none'
                    }
                },
                levels: [{
                    level: 1,
                    dataLabels: {
                        enabled: true
                    },
                    borderWidth: 2,
                    borderColor: '#ffffff'
                }]
            }
        },
        series: [{
            type: 'treemap',
            layoutAlgorithm: 'squarified',
            data: data,
            color: '#EEE3FF',
            colorByPoint: false
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

export default TreeMapChart
