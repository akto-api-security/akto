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

            <div>
                <span class="heading">API Testing Results</span>
            </div>

            <simple-table
                :headers="endpointHeaders" 
                :items="flattenedTestingRunResults" 
                name="API Testing Results" 
                @rowClicked="goToEndpoint"
            >
                <template #item.tests="{item}">
                    <sensitive-chip-group :sensitiveTags="Array.from(item.tests || new Set())" />
                </template>
            </simple-table>

        </div>
    </simple-layout>
</template>

<script>

import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'

import func from '@/util/func'
import { mapState } from 'vuex'

export default {
    name: "PageTesting",
    components: {
        SimpleLayout,
        SimpleTable,
        SensitiveChipGroup
    },
    props: {

    },
    data() {
        return  {
            newKey: this.nonNullAuth ? this.nonNullAuth.key : null,
            newVal: this.nonNullAuth ? this.nonNullAuth.value: null,
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