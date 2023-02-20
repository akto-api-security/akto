<template>
    <div style="padding: 24px; height: 100%" v-if="(!isSelectedRoleEmpty || createNew)">
        <v-container>
            <div style=" display: flex">
                <div class="form-text">
                    Role Name
                </div>
                <div style="padding-top: 0px">
                    <v-text-field :placeholder="(selectedRole.name ? selectedRole.name : 'Define role name')" flat solo
                        class="form-value" v-model="roleName" :rules="name_rules" hide-details
                        :readonly="!(createNew)" />
                </div>
            </div>
            <div v-if="!isSelectedRoleEmpty">
                <div style=" display: flex">
                    <div class="form-text">
                        Creator
                    </div>
                    <div style="padding-top: 0px">
                        <v-text-field flat solo class="form-value" readonly hide-details
                            :value="selectedRole.createdBy" />
                    </div>
                </div>
                <div style="display: flex">
                    <div class="form-text">
                        Last Updated
                    </div>
                    <div style="padding-top: 0px">
                        <v-text-field flat solo class="ma-0 pa-0" readonly hide-details
                            :value='computeLastUpdated(selectedRole.lastUpdatedTs)' />
                    </div>
                </div>
            </div>
            <div>
                <v-row style="padding: 36px 12px 12px 12px">
                    <test-role-conditions-table initial_string="Endpoint" :selectedRole="selectedRole"
                        table_header="Role endpoint conditions" :operators="operators"
                        :requireTextInputForTypeArray="requireTextInputForTypeArray"
                        :requireMapInputForTypeArray="requireMapInputForTypeArray" :operation_types="operation_types" />
                </v-row>
            </div>

            <div class="d-flex pt-4">
                <div class="form-text">
                    Auth token
                </div>

                <div class="input-value">
                    <v-text-field v-model="newKey" style="width: 200px">
                        <template slot="label">
                            <div class="d-flex">
                                Auth header key
                            </div>
                        </template>
                    </v-text-field>

                </div>
                <div class="input-value">
                    <v-text-field v-model="newVal" style="width: 500px">
                        <template slot="label">
                            <div class="d-flex">
                                Auth header value
                            </div>
                        </template>

                    </v-text-field>
                </div>
            </div>


            <v-row style="padding-top: 30px">
                <div style="padding: 12px">
                    <v-btn @click="save" color="#6200EA" class="save-btn" height="40px" width="100px"
                        :loading="saveLoading">
                        Save
                    </v-btn>
                </div>
            </v-row>
            <!-- <review-table v-if="reviewData" :review-data="reviewData" /> -->
        </v-container>
    </div>
</template>


<script>
import ReviewTable from "@/apps/dashboard/views/settings/components/data_types/components/ReviewTable";
import TestRoleConditionsTable from "./TestRoleConditionsTable.vue"
import { mapState } from "vuex";
import func from "@/util/func";
import api from "../api"

export default {
    name: "TestRolesConfigDetails",
    props: {
    },
    components: {
        ReviewTable,
        TestRoleConditionsTable
    },
    data() {
        var operators = [
            'OR',
            'AND'
        ]
        var requireTextInputForTypeArray = [
            'CONTAINS'
        ]
        var requireMapInputForTypeArray = [
            'BELONGS_TO',
            'NOT_BELONGS_TO'
        ]
        var operation_types = [
            { value: 'CONTAINS', text: 'contains', operators: ['OR', 'AND'] },
            { value: 'BELONGS_TO', text: 'belongs to', operators: ['OR'] },
            { value: 'NOT_BELONGS_TO', text: 'does not belongs to', operators: ['AND'] }
        ]
        return {
            operators,
            operation_types,
            requireTextInputForTypeArray,
            requireMapInputForTypeArray,
            saveLoading: false,
            reviewLoading: false,
            roleName: "",
            newKey: "",
            newVal: "",
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
        computeLastUpdated(timestamp) {
            let t = timestamp
            if (t) {
                return func.prettifyEpoch(t)
            } else if (t === 0) {
                return func.prettifyEpoch(1667413800)
            }
        },

        filterContainsConditions(operator) {//operator is string as 'OR' or 'AND'
            let filteredCondition = {}
            let found = false
            filteredCondition['operator'] = operator
            filteredCondition['predicates'] = []
            this.conditions.forEach(element => {
                if (element.value && element.operator === operator) {
                    if (element.type === 'CONTAINS') {
                        filteredCondition['predicates'].push({ type: element.type, value: element.value })
                        found = true
                    } else if (element.type === 'BELONGS_TO') {
                        let collectionMap = element.value
                        let collectionId = Object.keys(collectionMap)[0]

                        if (collectionMap[collectionId]) {
                            let apiKeyInfoList = []
                            collectionMap[collectionId].forEach(apiKeyInfo => {
                                if (apiKeyInfo['checked']) {
                                    apiKeyInfoList.push({ 'url': apiKeyInfo['url'], 'method': apiKeyInfo['method'], 'apiCollectionId': apiKeyInfo['apiCollectionId'] })
                                    found = true
                                }
                            })
                            if (apiKeyInfoList.length > 0) {
                                filteredCondition['predicates'].push({ type: element.type, value: apiKeyInfoList })
                            }
                        }
                    } else if (element.type === 'NOT_BELONGS_TO') { //Not belongs condition
                        let collectionMap = element.value
                        let collectionId = Object.keys(collectionMap)[0]

                        if (collectionMap[collectionId]) {
                            let apiKeyInfoList = []
                            collectionMap[collectionId].forEach(apiKeyInfo => {
                                if (apiKeyInfo['checked']) {
                                    apiKeyInfoList.push({ 'url': apiKeyInfo['url'], 'method': apiKeyInfo['method'], 'apiCollectionId': apiKeyInfo['apiCollectionId'] })
                                    found = true
                                }
                            })
                            if (apiKeyInfoList.length > 0) {
                                filteredCondition['predicates'].push({ type: element.type, value: apiKeyInfoList })
                            }
                        }
                    }
                }
            });
            if (found) {
                return filteredCondition;
            }
        },

        async save() {
            let andConditions = this.filterContainsConditions('AND')
            let orConditions = this.filterContainsConditions('OR')
            if (this.selectedRole && !this.isSelectedRoleEmpty) {// Update case
                let roleName = this.selectedRole.name

                if (andConditions || orConditions) {
                    this.saveLoading = true
                    await this.$store.dispatch('test_roles/updateTestRoles', {
                        roleName,
                        andConditions,
                        orConditions,
                        authParamData: [{
                            "key": this.newKey,
                            "value": this.newVal,
                            "where": "HEADER"
                        }]                        
                    })
                        .then((resp) => {
                            this.saveLoading = false

                            window._AKTO.$emit('SHOW_SNACKBAR', {
                                show: true,
                                text: `Role updated successfully!`,
                                color: 'green'
                            })
                        }).catch((err) => {
                            this.saveLoading = false
                        })
                } else {
                    window._AKTO.$emit('SHOW_SNACKBAR', {
                        show: true,
                        text: `All values are empty`,
                        color: 'red'
                    })
                }
            } else {//Create new case
                let roleName = this.roleName
                if (andConditions || orConditions) {
                    this.saveLoading = true
                    await this.$store.dispatch('test_roles/addTestRoles', {
                        roleName,
                        andConditions,
                        orConditions
                    })
                        .then((resp) => {
                            this.saveLoading = false

                            window._AKTO.$emit('SHOW_SNACKBAR', {
                                show: true,
                                text: `Role saved successfully!`,
                                color: 'green'
                            })
                        }).catch((err) => {
                            this.saveLoading = false
                        })
                } else {
                    window._AKTO.$emit('SHOW_SNACKBAR', {
                        show: true,
                        text: `All values are empty`,
                        color: 'red'
                    })
                }
            }
        }
    },
    mounted() {
    },
    computed: {
        ...mapState('test_roles', ['testRoles', 'loading', 'selectedRole', 'listOfEndpointsInCollection', 'createNew', 'conditions']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        isSelectedRoleEmpty() {
            return Object.keys(this.selectedRole).length === 0
        }
    },
    watch: {
    }
}

</script>

<style lang="sass" scoped>
    .input-value
        padding-right: 8px
        color: #47466A

    .condition-row
        width: 100%
        border-style: solid
        padding: 16px
        border-color: rgba(71,70,106,0.2)
        border-top-width: 0.5px
        border-bottom-width: 1px
        border-left-width: 1px
        border-right-width: 1px
        &.first
            border-top-width: 1px 

    .sensitive-text
        font-size: 16px !important
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
        font-size: 16px !important
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
        font-size: 16px !important
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
        font-size: 16px !important
        width: 200px 
        align-items: center
        display: flex
    
    .inline-block-child .value_predicate .v-text-field__slot input 
        color: #00f !important
    
        
</style>

<style scoped>
.v-input,
.v-input input,
.v-input textarea {
    color: #47466a !important
}

.v-text-field >>> input {
    font-size: 16px
}
</style>