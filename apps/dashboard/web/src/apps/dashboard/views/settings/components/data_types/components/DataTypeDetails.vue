<template>
    <div style="padding: 24px; height: 100%" v-if="data_type_copy">
        <v-container>
            <div style=" display: flex">
                <div class="form-text">
                    Name
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        placeholder="Add data type name"
                        flat solo
                        class="form-value"
                        :readonly="data_type_copy['createNew'] ? false:true"
                        v-model="data_type_copy.name"
                        :rules="name_rules"
                        hide-details
                    />
                </div>
            </div>

            <div style=" display: flex" v-if="data_type_copy.creatorId">
                <div class="form-text">
                    Creator
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        flat solo class="ma-0 pa-0"
                        readonly
                        hide-details
                        :value= computeUsername
                    />
                </div>
            </div>

            <div style="display: flex" v-if="data_type_copy.timestamp || data_type_copy.timestamp==0">
                <div class="form-text">
                    Last Updated
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        flat solo class="ma-0 pa-0"
                        readonly
                        hide-details
                        :value= computeLastUpdated
                    />
                </div>
            </div>

          <div v-if="data_type_copy.id || data_type_copy.createNew">
            <v-row style="padding: 36px 12px 12px 12px"  >
                    <conditions-table
                    :conditions="data_type_copy.keyConditions"
                    initial_string="param_name"
                    table_header="Key conditions"
                    />
                </v-row>
                <v-row
                    style="padding: 12px 12px 12px 12px"
                    v-if="showMainOperator" 
                >
                    <operator-component :operator="data_type_copy.operator" @operatorChanged="operatorChanged"/>
                </v-row>

                <v-row style="padding: 12px; padding-bottom: 50px"  >
                    <conditions-table
                    :conditions="data_type_copy.valueConditions"
                    initial_string="param_value"
                    table_header="Value conditions"
                    />
                </v-row>
            </div>
            <div style="display: flex">
                <div class="form-text">
                    Sensitive in Request
                </div>
                <v-btn-toggle
                v-model="reqToggle"
                mandatory >
                    <v-btn value="req" :class="data_type_copy.sensitiveAlways || (data_type_copy.sensitivePosition && ( data_type_copy.sensitivePosition.includes('REQUEST_PAYLOAD') || data_type_copy.sensitivePosition.includes('REQUEST_HEADER') ) )? 'sensitive-text true' : 'sensitive-text'"
                    @click="toggleSensitiveRequest">
                        true
                    </v-btn>
                    <v-btn value="req" :class="data_type_copy.sensitiveAlways || (data_type_copy.sensitivePosition && ( data_type_copy.sensitivePosition.includes('REQUEST_PAYLOAD') || data_type_copy.sensitivePosition.includes('REQUEST_HEADER') ) )? 'sensitive-text' : 'sensitive-text false'"
                    @click="toggleSensitiveRequest">
                        false
                    </v-btn>
                </v-btn-toggle>
            </div>
            <div style="display: flex">
                <div class="form-text">
                    Sensitive in Response
                </div>
                <v-btn-toggle
                v-model="resToggle"
                mandatory>
                    <v-btn value="res" :class="data_type_copy.sensitiveAlways || (data_type_copy.sensitivePosition && ( data_type_copy.sensitivePosition.includes('RESPONSE_PAYLOAD') || data_type_copy.sensitivePosition.includes('RESPONSE_HEADER') ) )? 'sensitive-text true' : 'sensitive-text'"
                    @click="toggleSensitiveResponse">
                        true
                    </v-btn>
                    <v-btn value="res" :class="data_type_copy.sensitiveAlways || (data_type_copy.sensitivePosition && ( data_type_copy.sensitivePosition.includes('RESPONSE_PAYLOAD') || data_type_copy.sensitivePosition.includes('RESPONSE_HEADER') ) )? 'sensitive-text' : 'sensitive-text false'"
                    @click="toggleSensitiveResponse">
                        false
                    </v-btn>
                </v-btn-toggle>
            </div>
            <v-row style="padding-top: 30px">
                <div style="padding: 12px">
                  <v-btn
                        @click="save"
                        color="#6200EA"
                        class="save-btn"
                        height="40px"
                        width="100px"
                        :loading="saveLoading"
                  >
                    Save
                  </v-btn>
                </div>
                <div v-if="data_type_copy.id || data_type_copy.createNew" style="padding: 12px">
                  <v-btn
                        @click="reviewCustomDataType"
                        color="#white"
                        class="review-btn"
                        height="40px"
                        width="100px"
                        :loading="reviewLoading"
                    >
                      Review
                        <span slot="loader">
                            <v-progress-circular
                                :rotate="360"
                                :size="30"
                                :width="5"
                                :value="computeLoading"
                                color="#6200EA"
                            >
                            </v-progress-circular>
                            {{ computeLoading + "%"}}
                        </span>
                    </v-btn>
                </div>
            </v-row>
            <review-table v-if="reviewData" :review-data="reviewData"/>
        </v-container>
    </div>
</template>


<script>
import obj from "@/util/obj"
import ConditionsTable from './ConditionsTable.vue'
import OperatorComponent from './OperatorComponent.vue'
import ReviewTable from "@/apps/dashboard/views/settings/components/data_types/components/ReviewTable";
import {mapState} from "vuex";
import func from "@/util/func";
export default {
    name: "DataTypeDetails",
    props: {
    },
    components: {
        ConditionsTable,
        OperatorComponent,
        ReviewTable
    },
    data() {
        return {
            data_type_copy: null,
            saveLoading: false,
            reviewLoading: false,
            name_rules: [
                    value => {
                        if (!value) return "Required"
                        const regex = /^[a-z0-9_]+$/i;
                        if (!value.match(regex)) return "Alphanumeric and underscore characters"
                        return true
                    },
                ],
        }
    },
    methods: {
        toggleSensitiveRequest() {
            {
                let temp = []

                if (!this.data_type_copy.sensitiveAlways && !this.data_type_copy.sensitivePosition.includes("REQUEST_PAYLOAD")) temp.push("REQUEST_PAYLOAD")
                if (!this.data_type_copy.sensitiveAlways && !this.data_type_copy.sensitivePosition.includes("REQUEST_HEADER")) temp.push("REQUEST_HEADER")
                if (this.data_type_copy.sensitiveAlways || this.data_type_copy.sensitivePosition.includes("RESPONSE_PAYLOAD")) temp.push("RESPONSE_PAYLOAD")
                if (this.data_type_copy.sensitiveAlways || this.data_type_copy.sensitivePosition.includes("RESPONSE_HEADER")) temp.push("RESPONSE_HEADER")
                this.data_type_copy.sensitivePosition = temp
                console.log(this.data_type_copy.sensitivePosition)

                this.data_type_copy.sensitiveAlways = this.data_type_copy.sensitivePosition.length == 4 ? true : false
                if(this.data_type_copy.sensitivePosition.length==4) this.data_type_copy.sensitivePosition=[]
            }
        },
        toggleSensitiveResponse() { 
            {
                let temp = []

                if (this.data_type_copy.sensitiveAlways || this.data_type_copy.sensitivePosition.includes("REQUEST_PAYLOAD")) temp.push("REQUEST_PAYLOAD")
                if (this.data_type_copy.sensitiveAlways || this.data_type_copy.sensitivePosition.includes("REQUEST_HEADER")) temp.push("REQUEST_HEADER")
                if (!this.data_type_copy.sensitiveAlways && !this.data_type_copy.sensitivePosition.includes("RESPONSE_PAYLOAD")) temp.push("RESPONSE_PAYLOAD")
                if (!this.data_type_copy.sensitiveAlways && !this.data_type_copy.sensitivePosition.includes("RESPONSE_HEADER")) temp.push("RESPONSE_HEADER")
                this.data_type_copy.sensitivePosition = temp
                console.log(this.data_type_copy.sensitivePosition)

                this.data_type_copy.sensitiveAlways = this.data_type_copy.sensitivePosition.length == 4 ? true : false
                if(this.data_type_copy.sensitivePosition.length==4) this.data_type_copy.sensitivePosition=[]
            }
        },
        operatorChanged(value) {
            this.data_type_copy.operator = value
        },
        save() {
            this.saveLoading = true
            
            if(this.data_type_copy.id || this.data_type_copy.createNew){
                this.$store.dispatch("data_types/createCustomDataType", {data_type: this.data_type_copy, save: true})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })
            } else {
                this.$store.dispatch("data_types/updateAktoDataType", {data_type: this.data_type_copy})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })
            }
        },
        reviewCustomDataType() {
          this.reviewLoading = true
          this.$store.dispatch("data_types/createCustomDataType", {data_type: this.data_type_copy, save: false})
              .then((resp) => {
                this.reviewLoading = false
              })
              .catch((err) => {
                this.reviewLoading = false
              })
        }
    },
    mounted() {
    },
    computed: {
      ...mapState('data_types', ['data_type', 'usersMap', 'reviewData', 'current_sample_data_count', 'total_sample_data_count']),
        computeSensitiveValueRequest() {
            if (this.data_type_copy) {
                return this.data_type_copy.sensitiveAlways || (this.data_type_copy.sensitivePosition.includes("REQUEST_PAYLOAD") || this.data_type_copy.sensitivePosition.includes("REQUEST_HEADER"))
            }
        },
        computeSensitiveValueResponse() {
            if (this.data_type_copy) {
                return this.data_type_copy.sensitiveAlways || (this.data_type_copy.sensitivePosition.includes("RESPONSE_PAYLOAD") || this.data_type_copy.sensitivePosition.includes("RESPONSE_HEADER"))
            }
        },
        showMainOperator() {
            return this.data_type_copy.keyConditions && this.data_type_copy.keyConditions.predicates &&
            this.data_type_copy.keyConditions.predicates.length > 0 &&
            this.data_type_copy.valueConditions && this.data_type_copy.valueConditions.predicates &&
            this.data_type_copy.valueConditions.predicates.length > 0
        },
        computeUsername() {
          if (this.data_type_copy.creatorId) {
              return this.usersMap[this.data_type_copy.creatorId]
          }
          return null
        },
        computeLastUpdated() {
          let t = this.data_type_copy.timestamp
          if (t) {
              return func.prettifyEpoch(t)
          } else if (t===0) {
            return func.prettifyEpoch(1667413800)
          }
        },
        computeLoading() {
            if (this.total_sample_data_count === 0) return 0
            let val = Math.round(100*this.current_sample_data_count / this.total_sample_data_count)
            if (val > 100) return 100
            return val
        }
    },
    watch: {
        data_type: function(newVal, oldVal) {
            this.data_type_copy = JSON.parse(JSON.stringify(newVal))
            this.$store.state.data_types.reviewData = null
        },
    }
}

</script>

<style lang="sass" scoped>
    .sensitive-text
        font-size: 16px
        font-weight: bold
        align-items: center
        display: flex
        padding: 0px
        padding-left: 12px
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        width: 100%
        height: 38px !important
        margin-bottom: 10px
        margin-top: 10px
        color: #47466A
        &:before
            background-color:#FFFFFF
        &.true
            color: #12B76A
            background-color: #D1E9DC
        &.false
            color: var(--v-redMetric-base)
            background-color: #F1CECD
        &:hover
            cursor: pointer

    .save-btn
        background-color: #6200EA !important
        font-size: 16px
        font-weight: 600
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        width: 100%
        height: 48px !important
        margin-bottom: 24px
        color: #FFFFFF

        &.v-btn--disabled
            opacity: 0.3

    .review-btn
        font-size: 16px
        font-weight: 600
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        width: 100%
        height: 48px !important
        margin-bottom: 24px
        color: #47466A


    .form-text
        color: grey
        font-size: 16px
        width: 200px
        align-items: center
        display: flex
    
</style>

<style>
    .v-input, .v-input input, .v-input textarea {
        color:  #47466a !important
    }
</style>