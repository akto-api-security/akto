<template>
    <simple-layout title="API Testing" class="page-testing">
        <div class="pa-8">
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

            <simple-table
                :headers="endpointHeaders" 
                :items="flattenedTestingRunResults" 
                name="API Testing Results" 
                @rowClicked="openDetails"
            >
                <template #item.tests="{item}">
                    <sensitive-chip-group :sensitiveTags="Array.from(item.tests || new Set())" />
                </template>
            </simple-table>

            <v-dialog
                v-model="openDetailsDialog"
            >
                <div class="details-dialog">
                    <a-card
                        title="Test details"
                        color="rgba(33, 150, 243)"
                        subtitle=""
                        icon="$fas_stethoscope"
                    >
                        <template #title-bar>
                            <v-btn
                                plain
                                icon
                                @click="openDetailsDialog = false"
                                style="margin-left: auto"
                            >
                                <v-icon>$fas_times</v-icon>
                            </v-btn>
                        </template>
                        <div class="pa-4">
                            <sample-data :messages='requestAndResponse'/>
                        </div>
                    </a-card>
                </div>

            </v-dialog>

        </div>
    </simple-layout>
</template>

<script>

import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import ACard from '@/apps/dashboard/shared/components/ACard'
import SampleData from '@/apps/dashboard/shared/components/SampleData'

import api from './api'
import func from '@/util/func'
import { mapState } from 'vuex'

export default {
    name: "PageTesting",
    components: {
        SimpleLayout,
        SimpleTable,
        SensitiveChipGroup,
        ACard,
        SampleData
    },
    props: {

    },
    data() {
        return  {
            newKey: this.nonNullAuth ? this.nonNullAuth.key : null,
            newVal: this.nonNullAuth ? this.nonNullAuth.value: null,
            openDetailsDialog: false,
            requestAndResponse: null,
            endpointHeaders: [
                {
                    text: "color",
                    value: ""
                },
                {
                    text: "Endpoint",
                    value: "url"
                },
                {
                    text: "Method",
                    value: "method"
                },
                {
                    text: "Collection",
                    value: "collectionName"
                },
                {
                    text: "Tests",
                    value: "tests"
                },
                {
                    text: "Time",
                    value: "timestamp"
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
        async openDetails(row) {
            let r = await api.fetchRequestAndResponseForTest(row.x)
            this.requestAndResponse = r.testingRunResults && r.testingRunResults[0] ? Object.entries(r.testingRunResults[0].resultMap).filter(x => x[1].vulnerable).map(x => {return {message: x[1].message, title: x[0], highlightPaths:[]}}) : []
            this.openDetailsDialog = true
            console.log(r.testingRunResults[0])
        },
        getHighlightMap(x) {
            let v = x[1].privateParamTypeInfoList
            if (!v) return []
            v.forEach((y) => {
                y["highlightValue"] = "unique: " + y["uniqueCount"] + " public: " + y["publicCount"]
            })
            return v
        },
        goToEndpoint(row) {
            let routeObj = {
                name: 'apiCollection/urlAndMethod',
                params: {
                    apiCollectionId: row.x.apiInfoKey.apiCollectionId,
                    urlAndMethod: btoa(row.x.apiInfoKey.url+ " " + row.x.apiInfoKey.method)
                }
            }

            this.$router.push(routeObj)
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
        }
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

.details-dialog
    background-color: #FFFFFF
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

.details-dialog >>> .v-card {
    margin: 0px !important;
}
</style>