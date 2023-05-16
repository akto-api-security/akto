<template>
    <div>
      <v-btn
        plain
        icon
        @click="resetAndEmit"
        style="margin-right: auto"
        :ripple="false"
        v-if="stepNumber ==2"
        width=100
      >
        <div>
            <v-icon color="var(--themeColor)">$fas_angle-left</v-icon> <span class="btn-text"> Back</span>
        </div>
        
      </v-btn>
      <v-stepper v-model="stepNumber" elevation=0>
        <v-stepper-items>
          <v-stepper-content step="1">
            <integration-selector @connectorSelected="connectorSelected"/>
          </v-stepper-content>
          <v-stepper-content step="2">
            <component
              v-if="connectorType"
              v-bind="connectorType.props"
              :is="connectorType.component"
            />
          </v-stepper-content>
        </v-stepper-items>
      </v-stepper>
    </div>
</template>

<script>
import IntegrationSelector from "./IntegrationSelector"
import ACard from '@/apps/dashboard/shared/components/ACard'

export default {
  name: "IntegrationCenter",
  components: {
    ACard,
    IntegrationSelector
  },
  data () {
    return {
      stepNumber: 1,
      connectorType: null
    }
  },
  methods: {
    connectorSelected (connectorType) {
      this.connectorType = connectorType
      this.stepNumber ++
    },
    resetAndEmit() {
      this.stepNumber = 1
      this.connectorType = null
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

.btn-text
  color: var(--themeColor)
  vertical-align: text-top

</style>