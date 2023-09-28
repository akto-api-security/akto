<template>
  <chart
      :options="chartOptions"
      :updateArgs="[true, false]"
      ref='highcharts'
      v-if="(data && data.length > 0)"
  />
  <div v-else class="no-data-chart">
    No trend data!
  </div>
</template>

<script>
import merge from "@/util/merge";
import obj from "@/util/obj";
import {Chart} from "highcharts-vue"
import func from "@/util/func";

export default {
  name: "StackedChart",
  props: {
    type: obj.strR,
    color: obj.strR,
    areaFillHex: obj.boolR,
    title: obj.strR,
    data: obj.arrR,
    height: obj.numR,
    defaultChartOptions: obj.objR,
    tooltipMetadata: obj.objN,
    backgroundColor: {
      type: String,
      default: "var(--white)"
    },
    text: obj.boolR
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
            formatter: function (tooltip) {

              let def = tooltip.defaultFormatter.call(this, tooltip);
              let tooltipMetadata = this?.points?.length > 0 ? this.points[0]?.series?.userOptions?.tooltipMetadata : undefined;
              if(tooltipMetadata?.[this.x]){
                if(tooltipMetadata[this.x]?.branch){
                  def.push(`<span>branch: ${tooltipMetadata[this.x].branch}</span><br/>`)
                }
                if(tooltipMetadata[this.x].repository){
                  def.push(`<span>repository: ${tooltipMetadata[this.x].repository}</span><br/>`)
                }
              }
              return def;
            },
            shared: true
          },
          plotOptions: {
            column: {
                stacking: 'normal',
                dataLabels: {
                    enabled: false
                }
            },
            series: {
              minPointLength: 5,
              cursor: 'pointer',
              point: {
                events: {
                  click: ({point}) => {
                    this.$emit('dateClicked', point.options.x);
                  }
                }
              }
            }
          },
          series: [
            ...Object.keys(this.data).map(key => {
              return {
                data: this.data[key].data,
                color: this.data[key].color,
                name: this.data[key].name,
                fillColor: this.areaFillHex ? fillColor : {},
                marker: {
                  enabled: this.data.length <= 2
                },
                yAxis: 0,
                tooltipMetadata: this.tooltipMetadata
              }
            }),
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
          ],
          legend: {
            enabled: false
          }
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