<template>
  <chart
      :options="chartOptions"
      :updateArgs="[true, false]"
      ref='highcharts'
      v-if="(data && data.length > 0) || (inputMetrics && inputMetrics.length > 0)"
  />
  <div v-else class="no-data-chart">
    No trend data!
  </div>
</template>

<script>
import merge from "@/util/merge";
import obj from "@/util/obj";
import {Chart} from "highcharts-vue"

export default {
  name: "LineChart",
  props: {
    type: obj.strR,
    color: obj.strR,
    areaFillHex: obj.boolR,
    title: obj.strR,
    data: obj.arrR,
    height: obj.numR,
    defaultChartOptions: obj.objR,
    backgroundColor: {
      type: String,
      default: "var(--white)"
    },
    text: obj.boolR,
    inputMetrics: {
      type: Array,
      default: function () {
        return []
      }
    }
  },
  components: {
    Chart
  },
  computed: {
    chartOptions: {
      get: function () {

        var fillColor = {
          linearGradient: {x1:0, y1:0, x2:0, y2:1},
          stops: [
            [0, this.color+"3b"],
            [1, "var(--white)"]
          ]
        }

        var dataForChart = []
        this.data.forEach((x,idx) => {
          let b = {
            data: x['data'],
            color: this.color,
            name: x['name'],
            fillColor: this.areaFillHex ? fillColor : {},
            marker: { enabled: x['data'].length <= 2},
            yAxis: 0
          }

          dataForChart.push(b)
        })

        var basicOpts = {
          chart: {
            type: this.type,
            height: (this.height)+'px',
            spacing: [5,0,0,0],
            backgroundColor: this.backgroundColor
          },
          title: {
            text: '',
            style: {
              "text-align": "left"
            }
          },
          tooltip: {
            shared: true
          },
          series: [
            ...dataForChart,
            ...this.inputMetrics.map((x, i) => {
              return {
                data: x.data,
                color: "#FF4DCA",
                name: x.name,
                marker: {
                  enabled: false,
                  symbol: 'circle'
                },
                fillColor: {
                  linearGradient: {x1:0, y1:0, x2:0, y2:1},
                  stops: [
                    [0, "var(--white)"],
                    [1, "var(--white)"]
                  ]
                },
                yAxis: (i+1)
              }
            })
          ],
          xAxis: [
            {
              type: 'datetime',
              dateTimeLabelFormats: {
                day: '%b \ %e',
                month: '%b'
              },
              title: 'Date',
              visible: this.text,
              gridLineWidth: '0px'
            }
          ],
          yAxis: [
              {
                title: this.title,
                visible: this.text,
                gridLineWidth: '0px',
                min : 0
              },
              ...this.inputMetrics.map((x,i) => {
                return {
                  title: x.name,
                  visible: true,
                  opposite: true,
                  min : 0
                }
              })
          ]
        }

        return merge.deepmerge(basicOpts, this.defaultChartOptions)
      },
      set: function (newVal) {
        if (this.$refs.highcharts) {
          this.$refs.highcharts.chart.update(newVal)
        }
      }
    }
  }
}
</script>

<style scoped lang="sass">
.no-data-chart
  color: var(--hexColor20)
  font-size: 18px
</style>