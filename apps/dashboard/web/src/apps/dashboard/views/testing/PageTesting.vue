<template>
    <layout-with-tabs title="API Testing" class="page-testing" :tabs='["Test results", "User config"]'>
        <template slot="Test results">
            <div class="py-8">
                <div>                
                    <layout-with-left-pane title="Run test">
                        <div>
                            <router-view :key="$route.fullPath"/>
                        </div>
                        <template #leftPane>
                            <v-navigation-drawer
                                v-model="drawer"
                                floating
                                width="250px"
                            >
                                <div class="nav-section">
                                    <api-collection-group
                                        :items=leftNavItems
                                    >
                                        <!-- <template #prependItem>
                                            <v-btn primary dark color="#6200EA" tile style="width: -webkit-fill-available" class="mt-8 mb-8">
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
            </div>
        </template>        
    </layout-with-tabs>
</template>

<script>

import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import ACard from '@/apps/dashboard/shared/components/ACard'
import SampleData from '@/apps/dashboard/shared/components/SampleData'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'

import func from '@/util/func'
import testing from '@/util/testing'
import { mapState } from 'vuex'
import api from './api'
import LayoutWithLeftPane from '@/apps/dashboard/layouts/LayoutWithLeftPane'
import ApiCollectionGroup from '@/apps/dashboard/shared/components/menus/ApiCollectionGroup'

export default {
    name: "PageTesting",
    components: {
        SimpleTable,
        SensitiveChipGroup,
        ACard,
        SampleData,
        LayoutWithTabs,
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
            drawer: null
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
        ...mapState('testing', ['testingRuns', 'authMechanism', 'testingRunResults', 'pastTestingRuns']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
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
                                title: testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/"+x.hexId+"/results",
                                active: true
                            }
                        })
                    ]
                },
                {
                    icon: "$fas_plus",
                    title: "Previous tests",
                    group: "/dashboard/testing/",
                    color: "rgba(246, 190, 79)",
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
                                title: testing.getCollectionName(x.testingEndpoints, this.mapCollectionIdToName),
                                link: "/dashboard/testing/"+x.hexId+"/results",
                                active: true
                            }
                        })
                    ]
                }
            ]
        }
    },
    mounted() {
        let now = func.timeNow()
        this.$store.dispatch('testing/loadTestingDetails', {startTimestamp: now - func.recencyPeriod, endTimestamp: now})
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