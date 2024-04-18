<template>
    <layout-with-tabs title="" :tabs='["Settings", "Access", "Analyze"]'>
        <template slot="Settings">
            <div style="height: 100%" v-if="(!isSelectedRoleEmpty || createNew)">
                <v-container style="padding: 12px 12px 12px 0px">
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

                    <role-auth-mechanism/>

                    <v-row style="padding-top: 30px">
                        <div style="padding: 12px">
                            <v-btn @click="save" color="var(--themeColor)" class="save-btn" height="40px" width="100px"
                                :loading="saveLoading">
                                Save
                            </v-btn>
                        </div>
                    </v-row>
                    <!-- <review-table v-if="reviewData" :review-data="reviewData" /> -->
                </v-container>
            </div>
        </template>
        <template slot="Access">
            <div v-if="!isSelectedRoleEmpty && !createNew">
                <simple-table 
                :headers="headers" 
                :items="roleToUrls" 
                >
                <template v-slot:add-new-row-btn="{}">
                    <div class="clickable download-csv d-flex">
                        <secondary-button
                            @click="createAccessMatrix"
                            text="Create Access Matrix"
                            color="var(--themeColor)" />

                        <secondary-button
                            @click="deleteAccessMatrix"
                            text="Delete Access Matrix"
                            color="var(--themeColor)" />

                    </div>
                </template>
                </simple-table>
            </div>
        </template>
        <template slot="Analyze">
          <div class="pa-4">
            <div class="grey-text pb-2">
              Analyze header values from sample data
            </div>
            <div style="width: 500px">
              <simple-text-field
                  :readOutsideClick="true"
                  placeholder="Enter comma-separated header names and press enter"
                  @changed="analyzeApiSamples"
              />
            </div>
            <div v-for="(sampleValues, headerName, index) in resultApiSamples" :key="'hh_'+index">
              <div class="fw-500 pt-2">{{headerName}}:</div>
              <div v-for="(counter, vv, ii) in sampleValues" :key="'vv_'+ii">
                <div class="pl-4 fs-12">{{vv}}: {{counter}}</div>
              </div>
            </div>
          </div>
        </template>
    </layout-with-tabs>
</template>


<script>
import ReviewTable from "@/apps/dashboard/views/settings/components/data_types/components/ReviewTable";
import SecondaryButton from '@/apps/dashboard/shared/components/buttons/SecondaryButton'
import TestRoleConditionsTable from "./TestRoleConditionsTable.vue"
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import ACard from '@/apps/dashboard/shared/components/ACard'
import LayoutWithTabs from "@/apps/dashboard/layouts/LayoutWithTabs"
import { mapState } from "vuex";
import func from "@/util/func";
import api from "../api"
import SimpleTextField from "@/apps/dashboard/shared/components/SimpleTextField.vue";
import RoleAuthMechanism from "@/apps/dashboard/views/testing/components/test_roles/components/RoleAuthMechanism.vue";

export default {
    name: "TestRolesConfigDetails",
    props: {
    },
    components: {
      RoleAuthMechanism,
      SimpleTextField,
        ReviewTable,
        SimpleTable,
        TestRoleConditionsTable,
        ACard,
        LayoutWithTabs,
        SecondaryButton
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
            rolesToUrls: [],
            headers: [
                {
                    text: '',
                    value: 'color'
                },
                {
                    text: 'method',
                    value: 'method'
                }, {
                    text: 'url',
                    value: 'url'
                }],
            conditionCollections: [],
            resultApiSamples: {}
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
            let roleName = '';
            let dispatchUrl = '';
            let successText = '';
            if (this.selectedRole && !this.isSelectedRoleEmpty) {// Update case
                roleName = this.selectedRole.name
                dispatchUrl = 'test_roles/updateTestRoles';
                successText = 'Role updated successfully!';
            } else {//Create new case
                roleName = this.roleName
                dispatchUrl = 'test_roles/addTestRoles';
                successText = 'Role saved successfully!';
            }
            if (andConditions || orConditions) {
                    this.saveLoading = true
                    await this.$store.dispatch(dispatchUrl, {
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
                                text: successText,
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
        },
        async createAccessMatrix() {
            await api.createMultipleAccessMatrixTasks(this.selectedRole.name || this.roleName)
        },
        async deleteAccessMatrix() {
            let result = await api.deleteAccessMatrix(this.selectedRole.name || this.roleName)
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `Access details deleted successfully`,
                color: 'green'
            })
        },
        async analyzeApiSamples(headerNames) {
          let apiCollectionIds = this.conditionCollections.map((collectionId) => parseInt(collectionId));
          let resultApiSamplesResp = await api.analyzeApiSamples(apiCollectionIds, headerNames.split(",").map(x => x.trim().toLowerCase()))
          this.resultApiSamples = resultApiSamplesResp.headerValues
        }
    },
    mounted() {
        api.fetchAccessMatrixUrlToRoles().then((resp) => {
            this.rolesToUrls = resp.accessMatrixRoleToUrls;
        })
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
        },
        roleToUrls() {
            this.conditions.map((condition) => { 
                    if(condition.value){
                        let key = Object.keys(condition.value)[0]
                        if(key!=0 && !this.conditionCollections.includes(key)){
                            this.conditionCollections.push(key);
                        }
                    }
            });
            return this.rolesToUrls[this.roleName] ? this.rolesToUrls[this.roleName] : [] ;
        }
    },
    watch: {
        selectedRole(newVal, oldVal){
            if(newVal!=oldVal){
                this.conditionCollections = []
                this.roleName = this.selectedRole.name;
                this.newKey = this.selectedRole.authMechanism?.authParams?.[0]?.key || ''
                this.newVal = this.selectedRole.authMechanism?.authParams?.[0]?.value || ''
            }
        }
    }
}

</script>

<style lang="sass" scoped>
    .input-value
        padding-right: 8px
        color: var(--themeColorDark)

    .condition-row
        width: 100%
        border-style: solid
        padding: 16px
        border-color: var(--themeColorDark13)
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
            color: var(--hexColor10)
            background-color: var(--white2)
        &.true
            color: var(--hexColor5)
            background-color: var(--hexColor25)
        &.false
            color: var(--hexColor28)
            background-color: var(--hexColor32)
        &:hover
            cursor: pointer
        &.v-btn:before
            background-color: var(--white)

    .save-btn
        background-color: var(--themeColor) !important
        font-size: 16px !important
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
        font-size: 16px !important
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
        font-size: 16px !important
        width: 200px 
        align-items: center
        display: flex
    
    .inline-block-child .value_predicate .v-text-field__slot input 
        color: var(--hexColor38) !important
    
        
</style>

<style scoped>
.v-input,
.v-input input,
.v-input textarea {
    color: var(--themeColorDark) !important
}

.v-text-field >>> input {
    font-size: 16px
}
</style>