<template>
    <layout-with-tabs title="API Testing" class="page-testing"
        :tabs='["Test results", "User config", "Roles", "Rate Limit"]' :tab="tab" :disableHash="true">
        <template slot="Test results">
            <div class="py-8">
                <div>
                    <layout-with-left-pane title="Run test">
                        <div>
                            <router-view :key="$route.fullPath" />
                        </div>
                        <template #leftPane>
                            <v-navigation-drawer v-model="drawer" floating width="250px">
                                <div class="nav-section">
                                    <api-collection-group :items=leftNavItems>
                                        <!-- <template #prependItem>
                                            <v-btn primary dark color="var(--themeColor)" tile style="width: -webkit-fill-available" class="mt-8 mb-8">
                                                <div style="width: 100%">
                                                    <v-icon>$fas_plus</v-icon> 
                                                    New test
                                                </div>
                                            </v-btn>
                                        </template> -->
                                    </api-collection-group>
                                </div>
                            </v-navigation-drawer>
                        </template>
                    </layout-with-left-pane>
                </div>
            </div>
        </template>
        <template slot="User config">
            <div class="pa-8">
                <v-btn outlined color="var(--themeColor)" @click="stopAllTests" :loading="stopAllTestsLoading" style="float:right">
                    Stop all tests
                </v-btn>

                <div>

                    <div class="di-flex-bottom">
                        <div class="col_1">
                            <p> 1 </p>
                        </div>
                        <div>
                            <h3> Inject hard-coded attacker auth token </h3>
                        </div>
                    </div>
                    <!-- <div>
                        <span class="heading">Auth tokens</span>
                    </div> -->

                    <div class="d-flex">
                        <div class="input-value">
                            <v-text-field v-model="newKey" style="width: 200px">
                                <template slot="label">
                                    <div class="d-flex">
                                        Auth header key
                                        <help-tooltip :size="12"
                                            text="Please enter name of the header which contains your auth token. This field is case-sensitive. eg Authorization" />
                                    </div>
                                </template>
                            </v-text-field>

                        </div>
                        <div class="input-value">
                            <v-text-field v-model="newVal" style="width: 500px">
                                <template slot="label">
                                    <div class="d-flex">
                                        Auth header value
                                        <help-tooltip :size="12" text="Please enter the value of the auth token." />
                                    </div>
                                </template>

                            </v-text-field>
                        </div>

                        <v-btn primary dark color="var(--hexColor9)" @click="saveAuthMechanism" v-if="someAuthChanged">
                            Save changes
                        </v-btn>
                    </div>

                    <div class="di-flex-bottom">
                        <div class="col_1">
                            <p> 2 </p>
                        </div>
                        
                        <div>
                            <h3> Automate attacker auth token generation </h3>
                        </div>
                    </div>
                    
                    <div class="di-flex">
                        <div class="input-value">
                            <div  v-if="authTokenUrl != null && authTokenDate != null">
                                <span class="auth-token-title">URL: </span>
                                <span class="auth-token-text">{{authTokenUrl}}</span>
                                <br/>
                                <span class="auth-token-title">Created on: </span>
                                <span class="auth-token-text">{{authTokenDate}}</span>
                            </div>
                            <div  v-else-if="authTokenDate != null">
                                <span class="auth-token-title">Created on: </span>
                                <span class="auth-token-text">{{authTokenDate}}</span>
                            </div>
                        </div>
                        <v-btn primary dark color="var(--themeColor)" @click="toggleLoginStepBuilder">
                            <span v-if="originalDbState">Edit</span>
                            <span v-else>Create</span>
                        </v-btn>
                    </div>
                </div>


                <v-dialog v-model="showTokenAutomation" class="token-automation-modal">
                    <token-automation :originalDbState="originalDbState" @closeLoginStepBuilder=toggleLoginStepBuilder @toggleOriginalStateDb=toggleOriginalStateDb />
                </v-dialog>    
                
            </div>
        </template>
        <template slot="Roles">
            <test-roles title="Roles" :testRoles="testRoles" @refreshRoles="refreshRoles">
                <template #details-container="{}">
                    <test-roles-config-details></test-roles-config-details>
                </template>
            </test-roles>
        </template>
        <template slot="Logs" >
            <div>
                <log-fetch />
            </div>
        </template>
        <template slot="Rate Limit">
            <div class="ma-4">
                <simple-menu :items="maxRequestsPerMinOptions">
                    <template v-slot:activator2>
                        <span>Allowed requests / min: </span>
                        <span class="column-title clickable-line">{{ maxRequestsPerMinLabel }}</span>
                        <v-icon size="14" color="var(--themeColorDark)">$fas_angle-down</v-icon>
                    </template>
                </simple-menu>
            </div>
        </template>
    </layout-with-tabs>
</template>

<script>

import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import SampleData from '@/apps/dashboard/shared/components/SampleData'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import TestRoles from './components/test_roles/TestRoles'
import TestRolesConfigDetails from './components/test_roles/components/TestRolesConfigDetails'
import LogFetch from './LogFetch'


import func from '@/util/func'
import testing from '@/util/testing'
import { mapState } from 'vuex'
import api from './api'
import settingsApi from '../settings/api'
import LayoutWithLeftPane from '@/apps/dashboard/layouts/LayoutWithLeftPane'
import ApiCollectionGroup from '@/apps/dashboard/shared/components/menus/ApiCollectionGroup'
import LoginStepBuilder from './components/token/LoginStepBuilder'
import TokenAutomation from './components/token/TokenAutomation'
import HelpTooltip from '@/apps/dashboard/shared/components/help/HelpTooltip'
import SimpleMenu from "@/apps/dashboard/shared/components/SimpleMenu"

import obj from "@/util/obj";

export default {
    name: "PageTesting",
    components: {
        SimpleTable,
        SensitiveChipGroup,
        SampleData,
        LayoutWithTabs,
        LayoutWithLeftPane,
        ApiCollectionGroup,
        LoginStepBuilder,
        TestRoles,
        TestRolesConfigDetails,
        TokenAutomation,
        HelpTooltip,
        SimpleMenu,
        LogFetch
    },
    props: {
        tab: obj.strN
    },
    data() {
        let requestPerMinValues = [10, 20, 30, 60, 80, 100, 200, 300, 400, 600, 1000]
        let maxRequestsValues = requestPerMinValues.reduce((acc, val) => {
            acc.push({ "label": val, click: () => this.setMaxRequestsPerMin(val, val) })
            return acc
        }, [])
        return {
            originalDbState: null,
            stepBuilder: false,
            maxRequestsValues,
            newKey: this.nonNullAuth ? this.nonNullAuth.key : null,
            newVal: this.nonNullAuth ? this.nonNullAuth.value : null,
            stopAllTestsLoading: false,
            drawer: null,
            showLoginSaveOption: false,
            authMechanismData: {},
            testRoleName: "",
            testLogicalGroupRegex: "",
            showTokenAutomation: false,
            maxRequestPerMin: 0,
            maxRequestsPerMinLabel: "No Limit",
            maxRequestsPerMinOptions: [{ label: "No Limit", click: () => this.setMaxRequestsPerMin("No Limit", 0) }, ...maxRequestsValues]

        }
    },
    methods: {
        setAuthHeaderKey(newKey) {
            this.newKey = newKey
            this.saveAuth()
        },
        setMaxRequestsPerMin(label, requests) {
            this.maxRequestsPerMinLabel = label
            this.maxRequestPerMin = requests
            settingsApi.updateGlobalRateLimit(requests)
        },
        setAuthHeaderValue(newVal) {
            this.newVal = newVal
            this.saveAuth()
        },
        saveAuthMechanism() {
            this.$store.dispatch('testing/addAuthMechanism', {
                type: "HARDCODED", requestData: [], authParamData: [{
                    "key": this.newKey,
                    "value": this.newVal,
                    "where": "HEADER"
                }]
            })
        },
        async saveTestRoles() {
            if (this.testRoleName && this.testLogicalGroupRegex) {
                await this.$store.dispatch('testing/addTestRoles', { roleName: this.testRoleName, regex: this.testLogicalGroupRegex })
                this.$store.dispatch('test_roles/loadTestRoles')
            }
        },
        refreshRoles() {
            this.$store.dispatch('test_roles/loadTestRoles')
        },
        prepareItemForTable(x) {
            return {
                url: x.apiInfoKey.url,
                method: x.apiInfoKey.method,
                collectionName: this.mapCollectionIdToName[x.apiInfoKey.apiCollectionId],
                tests: Object.entries(x.resultMap).filter(y => y[1].vulnerable).map(y => y[0]),
                timestamp: func.prettifyEpoch(x.id.timestamp),
                x: x
            }
        },
        stopAllTests() {
            this.stopAllTestsLoading = true
            api.stopAllTests().then((resp) => {
                this.stopAllTestsLoading = false
                console.log(resp);
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: "All tests stopped!",
                    color: 'green'
                })
            }).catch((e) => {
                this.stopAllTestsLoading = false
            })
        },
        toggleLoginStepBuilder() {
            this.showTokenAutomation = !this.showTokenAutomation
        },
        toggleOriginalStateDb({reqData, authParamsList}) {
            let result = api.addAuthMechanism("LOGIN_REQUEST", reqData, authParamsList)
            result.then((resp) => {
                func.showSuccessSnackBar("Login Flow saved successfully!")
                
            }).catch((err) => {
                console.log(err);
            })

            console.log('got db state event')
            this.fetchAuthMechanismData()
        },
      validateMethod(methodName) {
          let m = methodName.toUpperCase()
          let allowedMethods = ["GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"]
          let idx = allowedMethods.indexOf(m);
          if (idx === -1) return null
          return allowedMethods[idx]
        },
        fetchAuthMechanismData() {
            api.fetchAuthMechanismData().then((resp) => {
                this.authMechanismData = resp.authMechanism;
                if (!this.authMechanismData) return
                let requestData = this.authMechanismData["requestData"]
                let str = JSON.stringify(this.authMechanismData);
                if (!requestData || requestData.length === 0) return

                let authParamData = this.authMechanismData["authParams"]
                if (!authParamData || authParamData.length === 0) return

                this.originalDbState = this.authMechanismData
            })
        }
    },
    computed: {
        ...mapState('test_roles', ['testRoles', 'loading', 'selectedRole']),
        ...mapState('testing', ['testingRuns', 'authMechanism', 'testingRunResults', 'pastTestingRuns','cicdTestingRuns']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        nonNullAuth() {
            return this.authMechanism && this.authMechanism.authParams && this.authMechanism.type == "HARDCODED" && this.authMechanism.authParams[0]
        },
        someAuthChanged() {
            let nonNullData = this.newKey != null && this.newVal != null && this.newKey != "" && this.newVal != ""
            if (this.nonNullAuth) {
                return nonNullData && (this.authMechanism.authParams[0].key !== this.newKey || this.authMechanism.authParams[0].value !== this.newVal)
            } else {
                return nonNullData
            }
        },
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        flattenedTestingRunResults() {
            return this.testingRunResults.filter(x => x.resultMap && Object.values(x.resultMap).find(y => y.vulnerable)).map(this.prepareItemForTable)
        },
        allTestingRunResults() {
            return this.testingRunResults.filter(x => x.resultMap && Object.values(x.resultMap).find(_y => true)).map(this.prepareItemForTable)
        },
        leftNavItems() {
            return [
                {
                    icon: "$fas_search",
                    active: true,
                    title: "Scheduled tests",
                    group: "/dashboard/testing/",
                    items: [
                        {
                            title: "All active tests",
                            link: "/dashboard/testing/active",
                            icon: "$fas_search",
                            class: "bold",
                            active: true
                        },
                        ...(this.testingRuns || []).map(x => {
                            if(x.id==="More button"){
                                return x;
                            }
                            return {
                                title: x.displayName || testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/" + x.hexId,
                                active: true
                            }
                        })
                    ]
                },
                {
                    icon: "$fas_search",
                    active: true,
                    title: "CI/CD tests",
                    group: "/dashboard/testing/",
                    color: "var(--rgbaColor17)",
                    items: [
                        {
                            title: "All CI/CD tests",
                            link: "/dashboard/testing/cicd",
                            icon: "$fas_search",
                            class: "bold",
                            active: true
                        },
                        ...(this.cicdTestingRuns || []).map(x => {
                            if(x.id==="More button"){
                                return x;
                            }
                            return {
                                title: x.displayName || testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/" + x.hexId,
                                active: true,
                                cicd: true
                            }
                        })
                    ]
                },
                {
                    icon: "$fas_plus",
                    title: "Previous tests",
                    group: "/dashboard/testing/",
                    color: "var(--rgbaColor1)",
                    active: true,
                    items: [
                        {
                            title: "All previous tests",
                            link: "/dashboard/testing/inactive",
                            icon: "$fas_plus",
                            class: "bold",
                            active: true
                        },
                        ...(this.pastTestingRuns || []).map(x => {
                            if(x.id==="More button"){
                                return x;
                            }
                            return {
                                title: x.name || testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/" + x.hexId,
                                active: true
                            }
                        })
                    ]
                }
            ]
        },
        authTokenUrl: function () {
            if (!this.authMechanismData) return null
            let requestData = this.authMechanismData["requestData"]
            if (!requestData || requestData.length === 0) return null
            return requestData[0]["url"]
        },
        authTokenDate: function () {
            if (!this.authMechanismData || this.authMechanismData.type == "HARDCODED") return null
            let id = this.authMechanismData["id"]
            if (!id) return null

            let date = id["date"]

            if (!date || date == "") return null

            let dayStartEpochMs = func.toDate(parseInt(date.slice(0, 10).replaceAll("-", "")))
            let dayStr = func.toDateStr(new Date(dayStartEpochMs), false)

            return dayStr + " " + date.slice(11)
        }
    },
    async mounted() {
        this.fetchAuthMechanismData()
        settingsApi.fetchAdminSettings().then(resp => {
            let accountSetting = resp.accountSettings
            if (accountSetting['globalRateLimit'] !== undefined && accountSetting['globalRateLimit'] !== 0) {
                this.maxRequestsPerMinLabel = accountSetting['globalRateLimit']
                this.maxRequestPerMin = accountSetting['globalRateLimit']
            }
        })
        let now = func.timeNow()
        this.$store.dispatch('test_roles/loadTestRoles')
        await this.$store.dispatch('testing/loadTestingDetails', { startTimestamp: now - func.recencyPeriod, endTimestamp: now })
    },
    watch: {
        authMechanism: {
            handler() {
                this.newKey = this.nonNullAuth ? this.nonNullAuth.key : null
                this.newVal = this.nonNullAuth ? this.nonNullAuth.value : null
            }
        }
    }
}
</script>

<style lang="sass" scoped>
.heading
    font-size: 16px
    color: var(--themeColorDark)
    font-weight: 500

.input-value
    padding-right: 8px
    color: var(--themeColorDark)

</style>

<style scoped>
.page-testing>>>.v-label {
    font-size: 12px;
    color: var(--themeColor);
    font-weight: 400;
}

.page-testing>>>input {
    font-size: 12px;
    font-weight: 400;
}

.col_1 {
    box-sizing: border-box;
    width: 24px;
    height: 24px;
    left: 0px;
    top: 0px;
    border: 2px solid var(--themeColor);
    border-radius: 50%;
    text-align: center;
    font-style: normal;
    font-weight: 600;
    font-size: 16px;
    line-height: 22px;
    color: var(--themeColor);
}

.di-flex {
    display: flex;
    gap: 16px;
    padding-bottom: 11px;
}

.di-flex-bottom {
    display: flex;
    gap: 8px;
    padding-top: 20px;
    padding-bottom: 11px;
}

.p_padding {
    opacity: 0.4;
    margin: 10px;
}

.token-automation-modal {
    width: 600px;
    height: 400px
}

.auth-token-title {
    font-size: 14px;
    font-weight: 600;
}

.auth-token-text {
    font-size: 14px;
}
</style>