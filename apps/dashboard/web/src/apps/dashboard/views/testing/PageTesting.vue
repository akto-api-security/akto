<template>
    <layout-with-tabs title="API Testing" :tabs='["Run Tests","Test results", "User config", "Roles"]' :tab="tab">
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
            <user-config class="user-config">
                <template v-slot:stop-all-tests>
                    <v-btn outlined color="var(--themeColor)" @click="stopAllTests" :loading="stopAllTestsLoading" style="float:right">
                        Stop All Tests
                    </v-btn>
                </template>
            </user-config>
        </template>
        <template slot="Roles">
            <test-roles title="Roles" :testRoles="testRoles">
                <template #details-container="{}">
                    <a-card title="Details" color="var(--rgbaColor2)" style="min-height: 600px">
                        <test-roles-config-details></test-roles-config-details>
                    </a-card>
                </template>
            </test-roles>
        </template>
        <template slot="Logs" >
            <div>
                <log-fetch />
            </div>
        </template>
        <template slot="Run Tests">
            <one-click-test />
        </template>
    </layout-with-tabs>
</template>

<script>

import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import ACard from '@/apps/dashboard/shared/components/ACard'
import SampleData from '@/apps/dashboard/shared/components/SampleData'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import TestRoles from './components/test_roles/TestRoles'
import TestRolesConfigDetails from './components/test_roles/components/TestRolesConfigDetails'
import LogFetch from './LogFetch'
import OneClickTest from './components/OneClickTest.vue'
import UserConfig from './components/token/UserConfig.vue'

import func from '@/util/func'
import testing from '@/util/testing'
import { mapState } from 'vuex'
import api from './api'
import LayoutWithLeftPane from '@/apps/dashboard/layouts/LayoutWithLeftPane'
import ApiCollectionGroup from '@/apps/dashboard/shared/components/menus/ApiCollectionGroup'
import LoginStepBuilder from './components/token/LoginStepBuilder'
import obj from "@/util/obj";

export default {
    name: "PageTesting",
    components: {
        SimpleTable,
        SensitiveChipGroup,
        ACard,
        SampleData,
        LayoutWithTabs,
        LayoutWithLeftPane,
        ApiCollectionGroup,
        LoginStepBuilder,
        TestRoles,
        TestRolesConfigDetails,
        LogFetch,
        OneClickTest,
        UserConfig
    },
    props: {
        tab: obj.strN
    },
    data() {
        return {
            testRoleName: "",
            testLogicalGroupRegex: "",
            drawer: null,
            stopAllTestsLoading:false,
        }
    },
    methods: {
        async saveTestRoles() {
            if (this.testRoleName && this.testLogicalGroupRegex) {
                await this.$store.dispatch('testing/addTestRoles', { roleName: this.testRoleName, regex: this.testLogicalGroupRegex })
                this.$store.dispatch('testing/loadTestRoles')
            }
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
        validateMethod(methodName) {
          let m = methodName.toUpperCase()
          let allowedMethods = ["GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"]
          let idx = allowedMethods.indexOf(m);
          if (idx === -1) return null
          return allowedMethods[idx]
        }
    },
    computed: {
        ...mapState('test_roles', ['testRoles', 'loading', 'selectedRole']),
        ...mapState('testing', ['testingRuns', 'testingRunResults', 'pastTestingRuns']),
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
                            return {
                                title: x.displayName || testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/" + x.hexId + "/results",
                                active: true
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
                            return {
                                title: x.displayName || testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/" + x.hexId + "/results",
                                active: true
                            }
                        })
                    ]
                }
            ]
        },
    },
    mounted() {
        let now = func.timeNow()
        this.$store.dispatch('test_roles/loadTestRoles')
        this.$store.dispatch('testing/loadTestingDetails', { startTimestamp: now - func.recencyPeriod, endTimestamp: now })
    }
}
</script>

<style lang="sass" scoped>
.heading
    font-size: 16px
    color: var(--themeColorDark)
    font-weight: 500
</style>

<style scoped>
.user-config>>>.v-label {
    font-size: 12px;
    color: var(--themeColor);
    font-weight: 400;
}

.user-config>>>input {
    font-size: 12px;
    font-weight: 400;
}
.p_padding {
    opacity: 0.4;
    margin: 10px;
}
</style>