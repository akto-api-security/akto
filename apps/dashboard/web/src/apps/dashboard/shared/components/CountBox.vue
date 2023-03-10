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
        Total: 'var(--themeColorDark13)',
        Pending: 'var(--rgbaColor8)',
        Overdue: 'var(--rgbaColor9)',
        Completed: 'var(--rgbaColor10)',
        'This week': 'var(--hexColor7)'
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
  color: var(--base)
  margin: auto

</style>