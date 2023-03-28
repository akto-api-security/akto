<template>
    <div class="pr-4 api-endpoints">
        <div>
            <div class="d-flex jc-end pb-3 pt-3">
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="var(--themeColorDark)" 
                                @click="refreshPage(false)"
                                v-on="on"
                                v-bind="attrs"
                            >
                                    <v-icon>$fas_redo</v-icon>
                            </v-btn>
                        </template>
                        Refresh
                    </v-tooltip>

                <upload-file fileFormat=".har" @fileChanged="handleFileChange" tooltipText="Upload traffic (.har)" label="" type="uploadTraffic"/>
                <icon-menu icon="$fas_download" :items="downloadFileItems"/>
            </div>
        </div>
        <div class="d-flex">
            <count-box title="Sensitive Endpoints" :count="this.sensitiveEndpointCount" colorTitle="Overdue"/>
            <count-box title="All Endpoints" :count="this.totalEndpointCount" colorTitle="Total"/>
        </div> 
        
        <layout-with-tabs title="" :tabs="['All', 'Sensitive', 'Unauthenticated', 'Workflows']">
            <template slot="actions-tray">
            </template>
            <template slot="All">

                <server-table 
                    :headers="allEndpointsTableHeaders" 
                    name="All" 
                    sortKeyDefault="discoveredTs" 
                    :sortDescDefault="true"
                    @rowClicked="allEndpointTableRowClicked"
                    :fetchParams="fetchAllTableParams"
                    :processParams="prepareItemForTable"
                    :getColumnValueList="getColumnValueList"
                    :rowsPerPageDefault="50"
                >
                
                 <template #add-at-top="{filters, filterOperators, total}">
                        <div style="align-items: center; display: flex;">
                            <v-tooltip>
                                <template v-slot:activator='{ on, attrs }'>
                                    <v-btn icon primary dark color="#47466A" @click="showScheduleDialog(filters, filterOperators, total)">
                                        <v-icon>$fas_play</v-icon>
                                    </v-btn>
                                </template>
                                "Run test"
                            </v-tooltip>
                            
                        </div>
                        
                    </template>
                <template #item.sensitiveTags="{item}">
                    <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                </template>

                </server-table>

            </template>
            <template slot="Sensitive">

                <server-table 
                    :headers="allEndpointsTableHeaders" 
                    name="Sensitive" 
                    sortKeyDefault="discoveredTs" 
                    :sortDescDefault="true"
                    @rowClicked="allEndpointTableRowClicked"
                    :fetchParams="fetchSensitiveTableParams"
                    :processParams="prepareItemForTable"
                    :getColumnValueList="getColumnValueList"
                    :rowsPerPageDefault="50"
                >

                    <template #item.sensitiveTags="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                    
                </server-table>
                
            </template>

            <template slot="Unauthenticated">

                <server-table 
                    :headers="allEndpointsTableHeaders" 
                    name="Sensitive"
                    sortKeyDefault="discoveredTs"
                    :sortDescDefault="true"
                    @rowClicked="allEndpointTableRowClicked"
                    :fetchParams="fetchUnauthenticatedTableParams"
                    :processParams="prepareItemForTable"
                    :getColumnValueList="getColumnValueList"
                    :rowsPerPageDefault="50"
                >
                    <template #item.sensitiveTags="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>

                </server-table>

            </template>
            <template slot="Workflows">
                <div>
                    <div class="d-flex jc-end ma-2">
                        <v-btn v-if="!showWorkflowTestBuilder" primary dark color="var(--themeColor)" @click="() => {originalStateFromDb = null; showWorkflowTestBuilder = true}">
                            Create new workflow
                        </v-btn>
                        <div style="align-items: center; display: flex; padding-right: 12px ">
                          <upload-file fileFormat=".json" @fileChanged="handleFileChange" tooltipText="Upload workflow" label="" type="uploadWorkflow"/>
                        </div>
                    </div>
                    <simple-table 
                        v-if="!showWorkflowTestBuilder"
                        :headers="workflowTestHeaders" 
                        :items="workflowTests"
                        @rowClicked="item => {originalStateFromDb = item; showWorkflowTestBuilder = true}"
                        name="Deprecated"
                    />
                    <div
                        v-if="showWorkflowTestBuilder"
                        width="80%"
                    >
                        <v-btn icon primary dark color="var(--themeColor)" class="float-right" @click="() => {originalStateFromDb = null; showWorkflowTestBuilder = false}">
                            <v-icon>$fas_times</v-icon>
                        </v-btn>
                        <workflow-test-builder :fetchAllEndpointsForWorkflow="this.fetchAllEndpointsForWorkflow" :apiCollectionId="apiCollectionId" :originalStateFromDb="originalStateFromDb" :defaultOpenResult="false" class="white-background"/>
                    </div>
                    
                
                </div>
            </template>
        </layout-with-tabs>
        <v-dialog v-model="showTestSelectorDialog" width="800px"> 
            <tests-selector :collectionName="apiCollectionName" @testsSelected=startTest v-if="showTestSelectorDialog"/>
        </v-dialog>
    </div>
</template>

<script>
import CountBox from '@/apps/dashboard/shared/components/CountBox'
import { mapState } from 'vuex'
import func from "@/util/func"
import obj from "@/util/obj"
import constants from '@/util/constants'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import api from '../api'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import TagChipGroup from '@/apps/dashboard/shared/components/TagChipGroup'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import { saveAs } from 'file-saver'
import UploadFile from '@/apps/dashboard/shared/components/UploadFile'
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
import IconMenu from '@/apps/dashboard/shared/components/IconMenu'
import WorkflowTestBuilder from './WorkflowTestBuilder'
import TestsSelector from './TestsSelector'
import SecondaryButton from '@/apps/dashboard/shared/components/buttons/SecondaryButton'
import ServerTable from '@/apps/dashboard/shared/components/ServerTable'

export default {
    name: "ApiEndpoints",
    components: { 
        CountBox, 
        LayoutWithTabs,
        SimpleTable,
        SensitiveChipGroup,
        TagChipGroup,
        Spinner,
        UploadFile,
        JsonViewer,
        IconMenu,
        WorkflowTestBuilder,
        TestsSelector,
        SecondaryButton,
        ServerTable
    },
    props: {
        apiCollectionId: obj.numR
    },
    activated(){
        this.refreshPage(true)
    },
    data() {
        return {
            file: null,
            rules: [
                value => !value || value.size < 50e6 || 'HAR file size should be less than 50 MB!',
            ],
            swaggerUploadRules: [
                    value => !value || value.size < 2e6 || 'JSON file size should be less than 2 MB!',
                ],
            swaggerFile: null,
            showMenu: false,
            allEndpointsTableHeaders: [
                {
                    text: '',
                    value: 'color',
                    hideFilter: true
                },
                {
                    text: 'Endpoint',
                    value: 'url',
                },
                {
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Sensitive Params',
                    value: 'sensitiveTags'
                },
                {
                  text: 'Last Seen',
                  value: 'lastSeenTs',
                  sortKey: 'lastSeenTs'
                },
                {
                  text: 'Access Type',
                  value: 'accessType'
                },
                {
                  text: 'Auth Type',
                  value: 'authType'
                },
                {
                    text: constants.DISCOVERED,
                    value: 'discoveredTs',
                    sortKey: 'discoveredTs'
                }
            ],
            tableHeaders: [
                {
                    text: '',
                    value: 'color',
                    hideFilter: true
                },
                {
                    text: 'Endpoint',
                    value: 'parameterisedEndpoint'
                },
                {
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Sensitive Params',
                    value: 'sensitiveTags'
                },
                {
                  text: 'Last Seen',
                  value: 'last_seen'
                },
                {
                  text: 'Access Type',
                  value: 'access_type'
                },
                {
                  text: 'Auth Type',
                  value: 'auth_type'
                },
                {
                    text: constants.DISCOVERED,
                    value: 'added'
                },
                {
                    text: 'Changes',
                    value: 'changes',
                    hideFilter: true
                }
            ],
            unusedHeaders: [
                {
                    text: '',
                    value: 'color'
                },                
                {
                    text: 'Endpoint',
                    value: 'endpoint'
                },
                {
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Last seen',
                    value: 'lastSeen'
                }
            ],
            downloadFileItems: [
                {
                    label: "Download OpenAPI Spec",
                    click: this.downloadOpenApiFile
                },
                {
                    label: "Export to Postman",
                    click: this.exportToPostman
                },
                {
                    label: "Download CSV file",
                    click: this.downloadData
                }
            ],
            showTestSelectorDialog: false,
            filteredItemsForScheduleTest: [],
            workflowTestHeaders: [
                {
                    text: '',
                    value: 'color'
                },                
                {
                    text: 'Test',
                    value: 'id'
                },
                {
                    text: 'Author',
                    value: 'author'
                }
            ],
            showWorkflowTestBuilder: false,
            originalStateFromDb: null,
            workflowTests: [],
            sensitiveDataKeys: [],
            totalEndpointCount: 0,
            sensitiveEndpointCount: 0,
            allEndpointsData: [],
            endpointDataQuery: {},
        }
    },
    methods: {
        allEndpointTableRowClicked(row) {
            this.$emit('selectedItem', {apiCollectionId: this.apiCollectionId || 0, urlAndMethod: row.url + " " + row.method, type: 2})
        },
        rowClicked(row) {
            this.$emit('selectedItem', {apiCollectionId: this.apiCollectionId || 0, urlAndMethod: row.endpoint + " " + row.method, type: 2})
        },
        async downloadData() {
            
            let allEndpointsData = []
            await api.fetchAllEndpointData(this.apiCollectionId).then(resp => {
                allEndpointsData = this.buildAllEndpointData(resp.allEndpoints)   
            })

            let headerTextToValueMap = Object.fromEntries(this.tableHeaders.map(x => [x.text, x.value]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",")+"\r\n"
            allEndpointsData.forEach(i => {
                csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, (this.apiCollectionName || "All endopints") + ".csv");
        },
        prettifyDate(ts) {
            if (ts)
                return func.prettifyEpoch(ts)
            else
                return '-'
        },
        handleFileChange({file, type}) {
            if (!file) {
                this.content = null
            } else {
                var reader = new FileReader();
                
                // Use the javascript reader object to load the contents
                // of the file in the v-model prop
                
                let isHar = file.name.endsWith(".har")
                let isJson = file.name.endsWith(".json")
                let isPcap = file.name.endsWith(".pcap")
                if (isHar || isJson) {
                    reader.readAsText(file)
                } else if (isPcap) {
                    reader.readAsArrayBuffer(new Blob([file]))
                }
                reader.onload = async () => {
                    let skipKafka = false;//window.location.href.indexOf("http://localhost") != -1
                    let apiCollectionId = this.apiCollectionId
                    if (isHar) {
                        await this.$store.dispatch('inventory/uploadHarFile', { content: JSON.parse(reader.result), filename: file.name, skipKafka, apiCollectionId})
                    } else if (isPcap) {
                        var arrayBuffer = reader.result
                        var bytes = new Uint8Array(arrayBuffer);

                        await api.uploadTcpFile([...bytes], this.apiCollectionId, skipKafka)
                    } else if (type === "uploadWorkflow") {
                        let resp = await this.$store.dispatch('inventory/uploadWorkflowJson', { content: reader.result, filename: file.name})
                        resp.workflowTests.forEach((x) => {
                          this.workflowTests.push({...x, color: "var(--white)"})
                        })
                    }
                }
            }
        },
        getColumnValueList(headerValue) {
            switch (headerValue) {
                case "method": 
                    return {
                        type: "STRING",
                        values: ["GET", "POST", "PUT", "HEAD", "OPTIONS"].map(x => {return {
                            title: x, 
                            subtitle: '',
                            value: x
                        }})
                    }
                
                case "sensitiveTags": 
                    return {
                        type: "STRING",
                        values: this.sensitiveDataKeys.map(x => {return {
                            title: x, 
                            subtitle: '',
                            value: x
                        }})
                    }
                
                case "accessType": 
                    return {
                        type: "STRING",
                        values: ["PUBLIC", "PRIVATE"].map(x => {return {
                            title: x, 
                            subtitle: '',
                            value: x
                        }})
                    }
                
                case "authType": 
                    return {
                        type: "STRING",
                        values: ["JWT", "CUSTOM", "AUTHENTICATED", "UNAUTHENTICATED", "BEARER", "BASIC"].map(x => {return {
                            title: x, 
                            subtitle: '',
                            value: x
                        }})
                    }

                case "discoveredTs":
                case "lastSeenTs": 
                    return {
                        type: "INTEGER",
                        values: {
                            min: 0,
                            max: 600
                        }
                    }
                 
                default: 
                    return  {type: "SEARCH", values: []}
            }
        },
        prepareItemForTable(x) {
            return {
                color: x.sensitiveParams.length > 0 ? this.$vuetify.theme.themes.dark.redMetric : this.$vuetify.theme.themes.dark.greenMetric,
                url: x.url,
                method: x.method,
                sensitiveTags: new Set(x.sensitiveParams),
                lastSeenTs: this.prettifyDate(x.lastSeenTs),
                accessType: x.accessType,
                authType: x.authTypes.join(', '),
                discoveredTs: this.prettifyDate(x.discoveredTs)
            }
        },

        async fetchAllTableParams(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            let query = this.buildFetchParamQuery(sortKey, sortOrder, skip, limit, filters, filterOperators)
            this.endpointDataQuery = query
            return api.fetchEndpointData(query, skip/50)
        },

        async fetchAllEndpointsForWorkflow(apiCollectionId) {
            let allEndpointsData = []
            await api.fetchAllEndpointData(apiCollectionId).then(resp => {
                allEndpointsData = this.buildAllEndpointData(resp.allEndpoints)   
            })
            return allEndpointsData
        },

        async fetchUnauthenticatedTableParams(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            let query = this.buildFetchParamQuery(sortKey, sortOrder, skip, limit, filters, filterOperators)

            let unauthenticatedKeyFound = false;
            for (let k in query.filterConditions) {
                let val = query.filterConditions[k]
                if (val.key == "authType"){
                    unauthenticatedKeyFound = true;
                }
            }

            if (!unauthenticatedKeyFound) {
                query.filterConditions.push({"key" : "authType", "operator": "AND", "values": ["UNAUTHENTICATED"]})
            }
            return api.fetchEndpointData(query, skip/50)
        },

        async fetchSensitiveTableParams(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            let query = this.buildFetchParamQuery(sortKey, sortOrder, skip, limit, filters, filterOperators)

            let sensitiveKeyFound = false;
            let finalSensitiveParams = this.sensitiveDataKeys
            for (let k in query.filterConditions) {
                let val = query.filterConditions[k]
                if (val.key == "sensitiveTags"){
                    sensitiveKeyFound = true;
                    if (query.filterConditions[k].operator == "NOT") {
                        finalSensitiveParams = finalSensitiveParams.filter(function(el) {
                            return val.values.indexOf(el) < 0;
                        });
                        query.filterConditions.push({"key" : "sensitiveTags", "operator": "OR", "values": finalSensitiveParams})
                        // query.filterConditions[k].values = finalSensitiveParams
                    }
                }
            }

            if (!sensitiveKeyFound) {
                query.filterConditions.push({"key" : "sensitiveTags", "operator": "OR", "values": finalSensitiveParams})
            }

            //check if sensitive is present, if not append, else modify value

            return api.fetchEndpointData(query, skip/50)
        },

        buildFetchParamQuery(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            let filterConditions = []
            filterConditions.push({"key" : "apiCollectionId", "operator": "OR", "values": [this.apiCollectionId]})

            for (let key in filters) {
                let values = Array.from(filters[key])
                let operator = filterOperators[key]
                
                if (operator == "AND" && (key == "method" || key == "accessType")) {
                    operator = "OR"
                }

                if (operator == "OR" && (key == "sensitiveTags" || key == "authType")) {
                    operator = "AND"
                }

                if ((key == "lastSeenTs" || key == "discoveredTs") && values.length > 0 ) {
                    values[0] = func.timeNow() - values[0] * 24 * 60 * 60
                    values[1] = func.timeNow() - values[1] * 24 * 60 * 60
                }

                if (values.length > 0) {
                    filterConditions.push({"key": key, "operator": operator, "values": values})
                }
            }
            let endpointQuery = {
                "filterConditions": filterConditions,
                "sortConditions": [
                    {
                        "key": sortKey,
                        "sortOrder": sortOrder
                    }
                ]
            }
            return endpointQuery
        },
        async downloadOpenApiFile() {
          let lastFetchedUrl = null;
          let lastFetchedMethod = null;
          let apiCollectionId = this.apiCollectionId
          for (let index =0; index < 10; index++) {
                var result = await this.$store.dispatch('inventory/downloadOpenApiFile', {apiCollectionId, lastFetchedUrl, lastFetchedMethod})
                let openApiString = result["openAPIString"]
                var blob = new Blob([openApiString], {
                    type: "application/json",
                });
                const fileName = "open_api_" +this.apiCollectionName+ ".json";
                saveAs(blob, fileName);

                lastFetchedUrl = result["lastFetchedUrl"]
                lastFetchedMethod = result["lastFetchedMethod"]

                if (!lastFetchedUrl || !lastFetchedMethod) break;
          }


          window._AKTO.$emit('SHOW_SNACKBAR', {
            show: true,
            text: "OpenAPI spec file downloaded !",
            color: 'green'
          })
        },

        async exportToPostman() {
          let apiCollectionId = this.apiCollectionId
          var result = await this.$store.dispatch('inventory/exportToPostman', {apiCollectionId})
          window._AKTO.$emit('SHOW_SNACKBAR', {
            show: true,
            text: "Exported to Postman!",
            color: 'green'
          })
        },

      handleSwaggerFileUpload() {
            if (!this.swaggerFile) {this.swaggerContent = null}
            var reader = new FileReader();
            
            reader.readAsText(this.swaggerFile);
            reader.onload = () => {
                this.$store.dispatch('inventory/saveContent', { swaggerContent: JSON.parse(reader.result), filename: this.swaggerFile.name, apiCollectionId : this.apiCollectionId})
            }
        },
        async refreshPage(shouldLoad) {
            // if (!this.apiCollection || this.apiCollection.length === 0 || this.$store.state.inventory.apiCollectionId !== this.apiCollectionId) {
            this.showWorkflowTestBuilder = false

            api.fetchCollectionEndpointCountInfo(this.apiCollectionId).then(resp => {
                this.totalEndpointCount = resp.totalEndpointCount
                this.sensitiveEndpointCount = resp.sensitiveEndpointCount                
            })

            this.workflowTests = (await api.fetchWorkflowTests()).workflowTests.filter(x => x.apiCollectionId === this.apiCollectionId).map(x => {
                return {
                    ...x,
                    color: "var(--white)"
                }
            })

            this.$emit('mountedView', {type: 1, apiCollectionId: this.apiCollectionId})
        },
        buildAllEndpointData(allEndpoints) {
            return func.buildAllEndpointData(allEndpoints)
        },
        showScheduleDialog(filters, filterOperators, total) {
            this.showTestSelectorDialog = true
            this.filteredItemsForScheduleTest = filteredItems
        },
        toApiInfoKeyList(listEndpoints) {
            return listEndpoints.map(x => {
                return {
                    url: x.endpoint,
                    method: x.method,
                    apiCollectionId: x.apiCollectionId
                }
            })
        },
        async startTest({recurringDaily, startTimestamp, selectedTests, testName, testRunTime, maxConcurrentRequests}) {
            let store = this.$store
            let apiCollectionId = this.apiCollectionId
            let endpointDataQuery = this.endpointDataQuery
            
            await store.dispatch('testing/scheduleTestForCollection', {apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, endpointDataQuery})
            
            this.showTestSelectorDialog = false            
        }  
    },
    computed: {
        apiCollectionName() {
            let collectionName = ""
            for (const [key, value] of this.$store.state.collections.apiCollections.entries()) {
                if (value.id == this.apiCollectionId) {
                    collectionName = value.displayName
                }
            }
            return collectionName
        },
    },
    async mounted() {
        api.fetchDataTypeNames().then((resp) => {
            this.sensitiveDataKeys = resp.allDataTypes
        })
    }
}
</script>

<style lang="sass">
.api-endpoints
    & .table-column
        &:nth-child(1)    
            width: 4px
            min-width: 4px
            max-width: 4px
        &:nth-child(2)    
            width: 350px
            min-width: 350px
        &:nth-child(3)    
            width: 150px
            min-width: 150px
            max-width: 150px
        &:nth-child(4)    
            width: 250px
            min-width: 250px
            max-width: 250px
        &:nth-child(5)    
            width: 200px
            min-width: 200px
            max-width: 200px
        &:nth-child(6)    
            width: 200px
            min-width: 200px
            max-width: 200px
.menu
    display: flex
    justify-content: right
</style>