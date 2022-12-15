<template>
    <div style="padding: 24px; height: 100%" v-if="(Object.keys(selectedRole) !== 0)">
        <v-container>
            <div style=" display: flex">
                <div class="form-text">
                    Role Name
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        placeholder="Define role name"
                        flat solo
                        class="form-value"
                        :readonly="(Object.keys(selectedRole).length !== 0)"
                        v-model="roleName"
                        :rules="name_rules"
                        hide-details
                    />
                </div>
            </div>
            <div style=" display: flex">
                <div class="form-text">
                    Where
                </div>
            </div>

          <div v-if="selected_role_copy.createNew">
            <v-row style="padding: 36px 12px 12px 12px"  >
                    <conditions-table
                    :conditions="selected_role_copy.keyConditions"
                    initial_string="param_name"
                    table_header="Key conditions"
                    />
                </v-row>
                <v-row
                    style="padding: 12px 12px 12px 12px"
                    v-if="showMainOperator" 
                >
                    <operator-component :operator="selected_role_copy.operator" @operatorChanged="operatorChanged"/>
                </v-row>
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
                <div v-if="selected_role_copy.id || selected_role_copy.createNew" style="padding: 12px">
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
import ConditionsTable from '@/apps/dashboard/views/settings/components/data_types/components/ConditionsTable.vue'
import OperatorComponent from '@/apps/dashboard/views/settings/components/data_types/components/OperatorComponent.vue'
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
            selected_role_copy: null,
            saveLoading: false,
            reviewLoading: false,
            roleName: "",
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
        operatorChanged(value) {
            this.selected_role_copy.operator = value
        },
        save() {
            this.saveLoading = true
            
            if(this.selected_role_copy.id || this.selected_role_copy.createNew){
                this.$store.dispatch("data_types/createCustomDataType", {data_type: this.selected_role_copy, save: true})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })
            } else {
                this.$store.dispatch("data_types/updateAktoDataType", {data_type: this.selected_role_copy})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })
            }
        },
        reviewCustomDataType() {
          this.reviewLoading = true
          this.$store.dispatch("data_types/createCustomDataType", {data_type: this.selected_role_copy, save: false})
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
        ...mapState('test_roles', ['testRoles', 'loading', 'selectedRole']),
        showMainOperator() {
            return this.selected_role_copy.keyConditions && this.selected_role_copy.keyConditions.predicates &&
            this.selected_role_copy.keyConditions.predicates.length > 0 &&
            this.selected_role_copy.valueConditions && this.selected_role_copy.valueConditions.predicates &&
            this.selected_role_copy.valueConditions.predicates.length > 0
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
            this.selected_role_copy = JSON.parse(JSON.stringify(newVal))
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
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        height: 38px !important
        margin-bottom: 6px
        margin-top: 6px
        &.inactive
            color: #475467
            background-color: #FCFCFD
        &.true
            color: #12B76A
            background-color: #E8FFF4
        &.false
            color: #F04438
            background-color: #FFE9E8
        &:hover
            cursor: pointer
        &.v-btn:before
            background-color: #FFFFFF

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