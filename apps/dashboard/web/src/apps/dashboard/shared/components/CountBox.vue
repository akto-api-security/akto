<template>
  <div class="count-box" :style="containerCSS()">
    <div class="count-value">{{toCommaSeparatedNumber(count)}}</div>
    <div class="count-title">{{title}}</div>
  </div>
</template>

<script>
import obj from "@/util/obj";
import func from "@/util/func";

export default {
  name: "CountBox",
  props: {
    count: obj.numR,
    title: obj.strR,
    colorTitle: {
      type: String,
      default: null
    }
  },
  methods: {
    toCommaSeparatedNumber(number) {
      return func.toCommaSeparatedNumber(number)
    },
    titleToTextColor() {
      var colors = func.actionItemColors()
      return colors[this.colorTitle || this.title]
    },
    titleToBackgroundColor() {
      var colors = {
        Total: 'rgba(71, 70, 106, 0.2)',
        Pending: 'rgba(246, 190, 79, 0.2)',
        Overdue: 'rgba(243, 107, 107, 0.2)',
        Completed: 'rgba(0, 191, 165, 0.2)',
        'This week': '#2193ef33'
      }

      return colors[this.colorTitle || this.title]
    },
    containerCSS() {
      var ret = {
        'color': this.titleToTextColor(),
        'background-color': this.titleToBackgroundColor()
      }

      return ret
    }
  }
}
</script>

<style scoped lang="sass">
.count-box
  border-radius: 6px
  display: flex
  flex-direction: column
  justify-content: space-around
  padding: 16px
  flex: 1 1 25%

  &:not(:last-child)
    margin-right: 16px

.count-value
  font-size: 24px
  font-weight: bold
  margin: auto

.count-title
  font-size: 11px
  color: #2d2434
  margin: auto

</style>