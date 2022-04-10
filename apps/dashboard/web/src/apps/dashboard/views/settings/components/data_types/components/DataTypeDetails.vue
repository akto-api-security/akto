<template>
    <div style="padding: 24px; height: 100%" v-if="data_type_copy">
        <v-container>
            <v-row style="height: 40px">
                <v-col cols="2">
                    <span style="color:grey; font-size: 16px; font-weight: bold">Name</span>
                </v-col>
                <v-col cols="9" style="padding-top: 15px">
                    <v-text-field
                        height="20px"
                        placeholder="Add data type name"
                        flat solo class="ma-0 pa-0" hide-details
                        :readonly="data_type_copy['createNew'] ? false:true"
                        v-model="data_type_copy.name"
                    />
                </v-col>
            </v-row>

            <v-row style="height: 40px" v-if="computeUsername">
                <v-col cols="2">
                    <span style="color:grey; font-size: 16px; font-weight: bold">Creator</span>
                </v-col>
                <v-col cols="9" style="padding-top: 12px; padding-left: 25px">
                    <span style="color:black; font-size: 16px">{{computeUsername}}</span>
                </v-col>
            </v-row>

            <v-row style="height: 40px" v-if="data_type_copy.timestamp">
              <v-col cols="2">
                <span style="color:grey; font-size: 16px; font-weight: bold">Last Updated</span>
              </v-col>
              <v-col cols="9" style="padding-top: 12px; padding-left: 25px">
                <span style="color:black; font-size: 16px">{{computeLastUpdated}}</span>
              </v-col>
            </v-row>

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
            <v-row style="height: 50px">
                <v-col cols="2">
                    <span style="color:grey; font-size: 16px; font-weight: bold">Sensitive</span>
                </v-col>
                <v-col
                    cols="9"
                    :class="data_type_copy.sensitiveAlways || data_type_copy.sensitivePosition.length > 0? 'sensitive-text true' : 'sensitive-text'"
                    @click="toggleSensitive"
                >
                    {{computeSensitiveValue}}
                </v-col>
            </v-row>
            <v-row v-if="data_type_copy.id || data_type_copy.createNew" style="padding-top: 30px">
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
                <div style="padding: 12px">
                  <v-btn
                        @click="reviewCustomDataType"
                        color="#white"
                        class="review-btn"
                        height="40px"
                        width="100px"
                        :loading="reviewLoading"
                    >
                      Review
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
            reviewData: null,
            saveLoading: false,
            reviewLoading: false
        }
    },
    methods: {
        toggleSensitive() {
            this.data_type_copy.sensitiveAlways = !this.data_type_copy.sensitiveAlways
        },
        operatorChanged(value) {
            this.data_type_copy.operator = value
        },
        save() {
            this.saveLoading = true
            this.$store.dispatch("data_types/createCustomDataType", {data_type: this.data_type_copy, save: true})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })

        },
        async reviewCustomDataType() {
          this.reviewLoading = true
          this.$store.dispatch("data_types/createCustomDataType", {data_type: this.data_type_copy, save: false})
              .then((resp) => {
                this.reviewData = resp
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
      ...mapState('data_types', ['data_type', 'usersMap']),
      computeSensitiveValue() {
            if (this.data_type_copy) {
                return this.data_type_copy.sensitiveAlways || this.data_type_copy.sensitivePosition.length > 0 ? "Yes" : "No"
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
          }
        }
    },
    watch: {
        data_type: function(newVal, oldVal) {
          console.log("TRIGGERED")
            this.data_type_copy = JSON.parse(JSON.stringify(newVal))
            this.reviewData = null
        },
    }
}

</script>

<style lang="sass" scoped>
    .sensitive-text
        padding-left: 24px
        &.true
            color: red
        &:hover
            cursor: pointer

    .save-btn
        color: white
        font-size: 20px
        font-weight: bold

    .review-btn
        color: black
        font-size: 20px




</style>