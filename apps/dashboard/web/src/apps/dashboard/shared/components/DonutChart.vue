<template>
  <div>
    <chart :options="chartOptions()" :updateArgs="[true, false]" ref='highcharts'/>
  </div>
</template>

<script>
import obj from "@/util/obj"
import {Chart} from 'highcharts-vue'

export default {

  name: "DonutChart",
  components: {
    Chart
  },
  props: {
      data: obj.arrR,
      name: obj.strR,
      size: obj.numR
  },
  methods: {
    chartOptions () {
      return  {
        chart: {
          type: 'pie',
          height: this.size + 3,
          width: this.size,
          marginTop: 12
        },
        title: {
          text: (this.data || []).reduce((z, e) => z + e.y, 0),
          y: 100
        },
        subtitle: {
          text: this.name,
          y: 120
        },
        tooltip: {
          enabled: false
        },
        plotOptions: {
          pie: {
            size: (this.size+'px'),
            borderWidth: (this.data && this.data.length > 1) ? 3 : 0,
            innerSize: '80%',
            dataLabels: {
              enabled: false
            }
          }
        },
        series:[{
          enableMouseTracking: false,
          name: this.name,
          colorByPoint: true,
          data: this.data
        }]
      }
    }
  }
}
</script>

<style scoped lang="sass">
</style>