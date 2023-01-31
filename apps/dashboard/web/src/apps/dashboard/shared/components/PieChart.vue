<template>
  <div>
    <chart :options="chartOptions()" :updateArgs="[true, false]" ref='highcharts'/>
  </div>
</template>

<script>
import obj from "@/util/obj"
import {Chart} from 'highcharts-vue'

export default {

  name: "PieChart",
  components: {
    Chart
  },
  props: {
    percent: obj.numR,
    fillColor: obj.strR,
    emptyColor: obj.strR,
    backgroundColor: obj.strR,
    size: obj.numR
  },
  methods: {
    chartOptions () {
      return  {
        chart: {
          type: 'pie',
          height: this.size + 3,
          width: this.size,
          marginTop: 12,
          backgroundColor: this.backgroundColor
        },
        title: {
          text: null
        },
        tooltip: {
          enabled: false
        },
        plotOptions: {
          pie: {
            size: (this.size+'px'),
            borderWidth: 0
          }
        },
        series:[{
          enableMouseTracking: false,
          name: '',
          colorByPoint: true,
          data:[{
            name: '',
            y: +this.percent,
            color: this.fillColor
          }, {
            name: '',
            y: 100 - +this.percent,
            color: this.emptyColor
          }]
        }]
      }
    }
  }
}
</script>

<style scoped lang="sass">
</style>