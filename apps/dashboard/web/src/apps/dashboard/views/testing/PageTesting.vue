<template>
    <simple-layout title="API Testing" class="page-testing">
        <div class="pa-8">
            <div>
                <layout-with-left-pane title="Run test">
                    <div>
                        <router-view/>
                    </div>
                    <template #leftPane>
                        <v-navigation-drawer
                            v-model="drawer"
                            floating
                            width="200px"
                        >
                            <div class="nav-section">
                                <api-collection-group
                                    :items=leftNavItems
                                >
                                    <template #prependItem>
                                        <v-btn primary dark color="#6200EA" tile style="width: -webkit-fill-available" class="mt-8 mb-8">
                                            <div style="width: 100%">
                                                <v-icon>$fas_plus</v-icon> 
                                                New test
                                            </div>
                                        </v-btn>
                                    </template>
                                </api-collection-group>
                            </div>
                        </v-navigation-drawer>
                    </template>
                </layout-with-left-pane>
            </div>
            <v-btn primary dark color="#6200EA" @click="stopAllTests" :loading="stopAllTestsLoading" style="float:right">
                Stop all tests
            </v-btn>
            <div>
                <span class="heading">Auth tokens</span>
            </div>

            <div class="d-flex">
                <div class="input-value">
                    <v-text-field 
                        v-model="newKey"
                        label="Auth header key"
                        style="width: 200px"
                    />
                </div>
                <div class="input-value">
                    <v-text-field 
                        v-model="newVal"
                        label="Auth header value"
                        style="width: 500px"
                    />                    
                </div>
            </div>

            <v-btn primary dark color="#6200EA" @click="saveAuthMechanism" v-if="someAuthChanged">
                Save changes
            </v-btn>

            <div class="pt-12">
                <span class="heading">API Testing Results</span>
            </div>

            <layout-with-tabs title="" :tabs="['Vulnerable', 'All']">
                <template slot="Vulnerable">
                    <test-results-table
                        :testingRunResults="flattenedTestingRunResults"
                        :showVulnerableOnly="true"
                    />
                </template>
                <template slot="All">
                    <test-results-table
                        :testingRunResults="allTestingRunResults"
                        :showVulnerableOnly="false"
                    />
                </template>
            </layout-with-tabs>
        </div>
    </simple-layout>
</template>

<script>

import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import ACard from '@/apps/dashboard/shared/components/ACard'
import SampleData from '@/apps/dashboard/shared/components/SampleData'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import TestResultsTable from './components/TestResultsTable'

import func from '@/util/func'
import { mapState } from 'vuex'
import api from './api'
import LayoutWithLeftPane from '@/apps/dashboard/layouts/LayoutWithLeftPane'
import ApiCollectionGroup from '@/apps/dashboard/shared/components/menus/ApiCollectionGroup'

export default {
    name: "PageTesting",
    components: {
        SimpleLayout,
        SimpleTable,
        SensitiveChipGroup,
        ACard,
        SampleData,
        LayoutWithTabs,
        TestResultsTable,
        LayoutWithLeftPane,
        ApiCollectionGroup
    },
    props: {

    },
    data() {
        return  {
            newKey: this.nonNullAuth ? this.nonNullAuth.key : null,
            newVal: this.nonNullAuth ? this.nonNullAuth.value: null,
            stopAllTestsLoading: false,
            drawer: null,
            leftNavItems: [
                {
                    icon: "$fas_search",
                    active: true,
                    title: "Active tests",
                    group: "/dashboard/testing/",
                    items: [
                        {
                            title: "L1",
                            link: "/dashboard/testing/123/results",
                            icon: "$fas_search",
                            active: true
                        },
                        {
                            title: "L2",
                            link: "/dashboard/testing/124/results",
                            link: "l2",
                            icon: "$fas_search"
                        }
                    ]
                },
                {
                    icon: "$fas_plus",
                    title: "Other tests",
                    group: "/dashboard/testing/m",
                    color: "rgba(246, 190, 79)",
                    active: true,
                    items: [
                        {
                            title: "m1",
                            link: "m1",
                            icon: "$fas_plus",
                            class: "alert",
                            active: true
                        },
                        {
                            title: "m2",
                            link: "m2",
                            icon: "$fas_plus",
                            class: "bold",
                            red: true
                        }
                    ]
                }
            ]
        }
    },
    methods: {
        setAuthHeaderKey(newKey) {
            this.newKey = newKey
            this.saveAuth()
        },
        setAuthHeaderValue(newVal) {
            this.newVal = newVal
            this.saveAuth()
        },
        saveAuthMechanism() {
            this.$store.dispatch('testing/addAuthMechanism', {key: this.newKey, value: this.newVal, location: "HEADER"})
        },
        prepareItemForTable(x){
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
        }
    },
    computed: {
        ...mapState('testing', ['testingRuns', 'authMechanism', 'testingRunResults']),
        nonNullAuth() {
            return this.authMechanism && this.authMechanism.authParams && this.authMechanism.authParams[0]
        },
        someAuthChanged () {
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
    },
    mounted() {
        this.$store.dispatch('testing/loadTestingDetails')        
        this.$store.dispatch('testing/loadTestingRunResults')        
        
    },
    watch: {
        authMechanism: {
            handler() {
                this.newKey = this.nonNullAuth ? this.nonNullAuth.key : null
                this.newVal = this.nonNullAuth ? this.nonNullAuth.value: null
            }
        }
    }
}
</script>

<style lang="sass" scoped>
.heading
    font-size: 16px
    color: #47466A
    font-weight: 500

.input-value
    padding-right: 8px
    color: #47466A

</style>

<style scoped>
.page-testing >>> .v-label {
  font-size: 12px;
  color: #6200EA;
  font-weight: 400;
}

.page-testing >>> input {
  font-size: 12px;
  font-weight: 400;
}

</style>