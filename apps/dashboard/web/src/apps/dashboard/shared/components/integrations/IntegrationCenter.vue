<template>
  <a-card
    title="Integrations Center"
    class="integrations-center-layout"
  >
    <template #title-bar>
      <v-btn
        plain
        icon
        @click="resetAndEmit"
        style="margin-left: auto"
      >
        <v-icon>$fas_times</v-icon>
      </v-btn>
    </template>
    <div>
      <v-stepper v-model="stepNumber">
        <v-stepper-items>
          <v-stepper-content step="1">
            <metric-selector @connectorSelected="connectorSelected"/>
          </v-stepper-content>
          <v-stepper-content step="2">
            <component
              v-if="connectorType"
              :is="connectorType.component"
              @sourceSelected="sourceSelected"
            />
          </v-stepper-content>
          <v-stepper-content step="3">
            <metric-details-form
              v-if="sampleHeaders.length > 1"
              :headers="sampleHeaders"
              :sample-rows="sampleRows"
              @submitMetricsDetails="submitMetricsDetails"
            />
            <div v-else>
              <div>Your data source has just 1 column, you need minimum 2.</div>
              <div>Please try another data source</div>
            </div>
          </v-stepper-content>
          <v-stepper-content step="4">
            <target-details-form
              v-if="columnsInfo"
              @submitTargetDetails="submitTargetDetails"
            >
              <template v-slot:summary>
                <integration-summary
                  :columns-info="columnsInfo"
                  :data-source-info="dataSourceInfo"
                  :headers="sampleHeaders"
                  :sample-rows="sampleRows.slice(1, 5)"
                />
              </template>
            </target-details-form>
          </v-stepper-content>
        </v-stepper-items>
      </v-stepper>
    </div>
  </a-card>
</template>

<script>
import MetricSelector from "./MetricSelector";
import ACard from "../ACard";
import GoogleSheetSource from "./GoogleSheetSource";
import MetricDetailsForm from "./MetricDetailsForm";
import TargetDetailsForm from "./TargetDetailsForm";
import IntegrationSummary from "./IntegrationSummary";

export default {
  name: "IntegrationCenter",
  components: {
    GoogleSheetSource,
    ACard,
    MetricSelector,
    MetricDetailsForm,
    TargetDetailsForm,
    IntegrationSummary
  },
  data () {
    return {
      stepNumber: 1,
      connectorType: null,
      dataSourceInfo: null,
      columnsInfo: null,
      sampleHeaders: [],
      sampleRows: [],
      dashboardId: null,
      targetValue: 0,
      targetDateRange: null,
      trackingPeriod: null
    }
  },
  methods: {
    connectorSelected (connectorType) {
      this.connectorType = connectorType
      this.stepNumber ++
    },
    sourceSelected (dataSourceInfo) {
      this.dataSourceInfo = dataSourceInfo
      this.sampleHeaders = dataSourceInfo.sampleData.headers
      this.sampleRows = dataSourceInfo.sampleData.rows
      this.stepNumber ++
    },
    submitMetricsDetails (columnsInfo) {
      this.columnsInfo = columnsInfo
      this.stepNumber ++
    },
    submitTargetDetails (targetInfo) {
      this.dashboardId = targetInfo.dashboard
      this.targetValue = targetInfo.targetValue
      this.targetDateRange = targetInfo.dateRange
      this.trackingPeriod = targetInfo.trackingPeriod
      let successStr = this.columnsInfo.valueRangeCol.text
      window._AKTO.$emit('SHOW_SNACKBAR', {
        show: true,
        text: `${successStr} added successfully!`,
        color: 'green'
      })

      this.resetAndEmit()
    },
    resetAndEmit() {
      this.stepNumber = 1
      this.connectorType = null
      this.dataSourceInfo = null
      this.columnsInfo = null
      this.sampleHeaders = []
      this.sampleRows = []

      this.$emit('close')
    }
  }
}
</script>

<style scoped lang="sass">
.integrations-center-layout
  width: 94%
  overflow: hidden !important
  margin: 0px !important

</style>