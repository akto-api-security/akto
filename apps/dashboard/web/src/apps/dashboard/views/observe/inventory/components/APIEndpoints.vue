<template>
    <spinner v-if="loading" />
    <div class="pr-4 api-endpoints" v-else>
        <div class="d-flex">
            <count-box title="Sensitive Endpoints" :count="sensitiveEndpoints.length" colorTitle="Overdue"/>
            <count-box title="Shadow Endpoints" :count="shadowEndpoints.length" colorTitle="Pending"/>
            <count-box title="Unused Endpoints" :count="unusedEndpoints.length" colorTitle="This week"/>
            <count-box title="All Endpoints" :count="allEndpoints.length" colorTitle="Total"/>
        </div>    

        <layout-with-tabs title="" :tabs="['All', 'Sensitive', 'Shadow', 'Unused', 'Upload']">
            <template slot="actions-tray">
                <div class="d-flex jc-end">
                    <upload-file fileFormat=".har,.pcap" @fileChanged="handleFileChange" label=""/>
                    <icon-menu icon="$fas_download" :items="downloadFileItems"/>
                </div>
            </template>
            <template slot="All">
                <simple-table 
                    :headers=tableHeaders 
                    :items=allEndpoints 
                    @rowClicked=rowClicked 
                    name="All" 
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="Sensitive">
                <simple-table 
                    :headers=tableHeaders 
                    :items=sensitiveEndpoints 
                    @rowClicked=rowClicked name="Sensitive"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="Shadow">
                <simple-table 
                    :headers=tableHeaders 
                    :items=shadowEndpoints 
                    @rowClicked=rowClicked 
                    name="Shadow"  
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="Unused">
                <simple-table 
                    :headers=unusedHeaders 
                    :items=unusedEndpoints 
                    name="Unused"
                />
            </template>
            <template slot="Upload">
                <v-file-input
                    :rules=swaggerUploadRules
                    show-size
                    label="Upload JSON file"
                    prepend-icon="$curlyBraces"
                    accept=".json"
                    @change="handleSwaggerFileUpload"
                    v-model=swaggerFile
                ></v-file-input>
                <json-viewer
                    v-if="swaggerContent"
                    :contentJSON="swaggerContent"
                    :errors="{}"
                />
            </template>
        </layout-with-tabs>

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
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import { saveAs } from 'file-saver'
import UploadFile from '@/apps/dashboard/shared/components/UploadFile'
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
import IconMenu from '@/apps/dashboard/shared/components/IconMenu'


export default {
    name: "ApiEndpoints",
    components: { 
        CountBox, 
        LayoutWithTabs,
        SimpleTable,
        SensitiveChipGroup,
        Spinner,
        UploadFile,
        JsonViewer,
        IconMenu
    },
    props: {
        apiCollectionId: obj.numR
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
            tableHeaders: [
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
                    text: 'Sensitive Params',
                    value: 'sensitive'
                },
                {
                    text: constants.DISCOVERED,
                    value: 'added',
                    sortKey: 'detectedTs'
                },
                {
                    text: 'Changes',
                    value: 'changes',
                    sortKey: 'changesCount'
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
                }
            ],
            documentedURLs: {},
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
            ]
        }
    },
    methods: {
        rowClicked(row) {
            this.$emit('selectedItem', {apiCollectionId: this.apiCollectionId || 0, urlAndMethod: row.endpoint + " " + row.method, type: 2})
        },
        groupByEndpoint(listParams) {
            func.groupByEndpoint(listParams)
        },
        downloadData() {
            let headerTextToValueMap = Object.fromEntries(this.tableHeaders.map(x => [x.text, x.value]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",")+"\r\n"
            this.allEndpoints.forEach(i => {
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
        isShadow(x) {
            return !(this.documentedURLs[x.endpoint] && this.documentedURLs[x.endpoint].indexOf(x.method) != -1)
        },
        isUnused(url, method) {
            return this.allEndpoints.filter(e => e.endpoint === url && e.method == method).length == 0
        },
        handleFileChange({file}) {
            if (!file) {
                this.content = null
            } else {
                var reader = new FileReader();
                
                // Use the javascript reader object to load the contents
                // of the file in the v-model prop
                
                let isHar = file.name.endsWith(".har")
                let isPcap = file.name.endsWith(".pcap")
                if (isHar) {
                    reader.readAsText(file)
                } else if (isPcap) {
                    reader.readAsArrayBuffer(new Blob([file]))
                }
                reader.onload = async () => {
                    let skipKafka = window.location.href.indexOf("http://localhost") != -1
                    if (isHar) {
                        await this.$store.dispatch('inventory/uploadHarFile', { content: JSON.parse(reader.result), filename: file.name, skipKafka})
                    } else if (isPcap) {
                        var arrayBuffer = reader.result
                        var bytes = new Uint8Array(arrayBuffer);

                        await api.uploadTcpFile([...bytes], this.apiCollectionId, skipKafka)
                    }
                }
            }
        },
        async downloadOpenApiFile() {
          var result = await this.$store.dispatch('inventory/downloadOpenApiFile')
          let openApiString = result["openAPIString"]
          var blob = new Blob([openApiString], {
            type: "application/json",
          });
          const fileName = "open_api_" +this.apiCollectionName+ ".json";
          saveAs(blob, fileName);
          window._AKTO.$emit('SHOW_SNACKBAR', {
            show: true,
            text: fileName + " downloaded !",
            color: 'green'
          })
        },
        async exportToPostman() {
          var result = await this.$store.dispatch('inventory/exportToPostman')
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
        }
    },
    computed: {
        ...mapState('inventory', ['apiCollection', 'apiCollectionName', 'loading', 'swaggerContent']),
        allEndpoints () {
            return func.groupByEndpoint(this.apiCollection)
        },
        sensitiveEndpoints() {
            return func.groupByEndpoint(this.apiCollection).filter(x => x.sensitive > 0)
        },
        shadowEndpoints () {
            return func.groupByEndpoint(this.apiCollection).filter(x => this.isShadow(x))
        },
        unusedEndpoints () {
            let ret = []
            Object.entries(this.documentedURLs).forEach(entry => {
                let endpoint = entry[0]
                entry[1].forEach(method => {
                    if(this.isUnused(endpoint, method)) {
                        ret.push({
                            endpoint, 
                            method,
                            color: func.actionItemColors()["This week"]
                        })
                    }
                })
            })
            return ret
        }
    },
    mounted() {
        if (!this.apiCollection || this.apiCollection.length === 0 || this.$store.state.inventory.apiCollectionId !== this.apiCollectionId) {
            this.$store.dispatch('inventory/loadAPICollection', { apiCollectionId: this.apiCollectionId})
        }
        api.getAllUrlsAndMethods(this.apiCollectionId).then(resp => {
            this.documentedURLs = resp.data || {}
        })
        this.$emit('mountedView', {type: 1, apiCollectionId: this.apiCollectionId})
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