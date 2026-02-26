/**
 * Hook to generate chart options
 * @param {boolean} enableLegends - Whether to enable chart legends
 * @returns {Function} Function that returns chart options
 */
export const useChartOptions = (enableLegends) => {
    const getChartOptions = () => {
        const options = {
            "plotOptions": {
                series: {
                    events: {
                        legendItemClick: function () {
                            var seriesIndex = this.index
                            var chart = this.chart
                            var series = chart.series[seriesIndex]

                            chart.series.forEach(function (s) {
                                s.hide()
                            })
                            series.show()

                            return false
                        }
                    }
                }
            }
        }

        if (enableLegends) {
            options["legend"] = { layout: 'vertical', align: 'right', verticalAlign: 'middle' }
        }

        return options
    }

    return getChartOptions
}
