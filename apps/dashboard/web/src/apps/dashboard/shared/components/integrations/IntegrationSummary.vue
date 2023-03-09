<template>
  <div>
    <div class="heading">Summary</div>
    <sample-data
      :headers="headers"
      :rows="sampleRows"
    ></sample-data>

    <div class="single-line-summary">
      <span>Data source: </span>
      <span class="custom-input">{{dataSourceInfo.worksheetName}}</span>
      <span>in</span>
      <v-icon :size="12" color="var(--hexColor8)">$fas_file-csv</v-icon>
      <span class="custom-input">{{dataSourceInfo.sheetName}}</span>
    </div>

    <div class="single-line-summary">
      <span>I want to track</span>
      <span class="custom-input">{{columnsInfo.valueRangeCol.text}}</span>
      <span>where dates are in</span>
      <span class="custom-input">{{ columnsInfo.dateRangeCol.text }}</span>
      <span>column</span>
    </div>

  </div>
</template>

<script>
import obj from "@/util/obj";
import SampleData from "./SampleData";

export default {
  name: "IntegrationSummary",
  components: {SampleData},
  props: {
    dataSourceInfo: obj.objR,
    columnsInfo: obj.objR,
    headers: obj.arrR,
    sampleRows: obj.arrR
  }
}
</script>

<style scoped lang="sass">
.single-line-summary
  padding-top: 20px
  & span
    font-size: 14px
    color: var(--themeColorDark)
    margin-left: 4px

  & .custom-input
    color: var(--themeColor) !important
    font-weight: 500

.heading
  font-size: 16px
  font-weight: 500
  color: var(--themeColorDark)
  padding-top: 20px
</style>