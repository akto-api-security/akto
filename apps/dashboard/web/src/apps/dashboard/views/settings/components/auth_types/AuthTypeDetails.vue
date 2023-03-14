<template>
    <div style="padding: 24px; height: 100%" v-if="auth_type_copy">
        <v-container>
            <div style=" display: flex">
                <div class="form-text">
                    Name
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        placeholder="auth type name"
                        flat solo
                        class="form-value"
                        :readonly="auth_type_copy['createNew'] ? false:true"
                        v-model="auth_type_copy.name"
                        :rules="name_rules"
                        hide-details
                    />
                </div>
            </div>

            <div style=" display: flex" v-if="auth_type_copy.creatorId">
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

            <div style="display: flex" v-if="auth_type_copy.timestamp">
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
            <div v-if="auth_type_copy.id || auth_type_copy.createNew">
            <v-row style="padding: 36px 12px 12px 12px"  >
                    <conditions-table
                    :conditions="auth_type_copy.headerKeyConditions"
                    :onlyEqual="true"
                    initial_string="key"
                    table_header="Header keys"
                    />
                </v-row>
                <v-row
                    style="padding: 12px 12px 12px 12px"
                >
                    <operator-component :operators="operators" :operator="auth_type_copy.operator" :onlyEqual="true"/>
                </v-row>
            <v-row style="padding: 12px"  >
                    <conditions-table
                    :conditions="auth_type_copy.payloadKeyConditions"
                    :onlyEqual="true"
                    initial_string="key"
                    table_header="Payload keys"
                    />
                </v-row>
            </div>
            <v-row v-if="auth_type_copy.id || auth_type_copy.createNew" style="padding-top: 30px">
                <div style="padding: 12px">
                  <v-btn
                        @click="save"
                        color="var(--themeColor)"
                        class="save-btn"
                        height="40px"
                        width="100px"
                        :loading="saveLoading"
                  >
                    Save
                  </v-btn>
                </div>
            </v-row>
        </v-container>
    </div>
</template>


<script>
import obj from "@/util/obj"
import ConditionsTable from '../data_types/components/ConditionsTable.vue'
import OperatorComponent from "../data_types/components/OperatorComponent.vue"
import {mapState} from "vuex";
import func from "@/util/func";
export default {
    name: "AuthTypeDetails",
    props: {
    },
    components: {
        ConditionsTable,
        OperatorComponent,
    },
    data() {
        var operators = [
                "OR"
            ]
        return {
            auth_type_copy: null,
            saveLoading: false,
            name_rules: [
                    value => {
                        if (!value) return "Required"
                        const regex = /^[a-z0-9_]+$/i;
                        if (!value.match(regex)) return "Alphanumeric and underscore characters"
                        return true
                    },
                ],
            operators
        }
    },
    methods: {
        save() {
            this.saveLoading = true
            this.$store.dispatch("auth_types/createAuthType", {auth_type: this.auth_type_copy, save: true})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })
        }
    },
    mounted() {
        this.auth_type_copy = this.$store.state.auth_types.auth_type
    },
    computed: {
      ...mapState('auth_types', ['auth_type', 'usersMap']),
        computeUsername() {
          if (this.auth_type_copy.creatorId) {
              return this.usersMap[this.auth_type_copy.creatorId]
          }
          return null
        },
        computeLastUpdated() {
          let t = this.auth_type_copy.timestamp
          if (t) {
              return func.prettifyEpoch(t)
          }
        }
    },
    watch: {
        auth_type: function(newVal, oldVal) {
            this.auth_type_copy = JSON.parse(JSON.stringify(newVal))
        },
    }
}

</script>

<style lang="sass" scoped>
    .sensitive-text
        font-size: 16px
        font-weight: bold
        width: 210px
        align-items: center
        display: flex
        padding: 0px
        padding-left: 12px
        color: var(--themeColorDark)
        &.true
            color: var(--v-redMetric-base)
        &:hover
            cursor: pointer

    .save-btn
        background-color: var(--themeColor) !important
        font-size: 16px
        font-weight: 600
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        width: 100%
        height: 48px !important
        margin-bottom: 24px
        color: var(--white)

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
        color: var(--themeColorDark)


    .form-text
        color: grey
        font-size: 16px
        width: 200px
        align-items: center
        display: flex
    
</style>

<style>
    .v-input, .v-input input, .v-input textarea {
        color:  var(--themeColorDark) !important
    }
</style>