<template>
    <div class="pr-4 api-endpoints">
        <simple-table 
            :headers="tableHeaders"
            :items="items"
            name="Parameter state"
            @rowClicked="rowClicked"
            sortKeyDefault="publicCount"
            :sortDescDefault="true"
        >
        </simple-table>
    </div>
</template>


<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import {mapState} from 'vuex'
import api from "./api"

export default {
    name: "ParamState",
    components: { 
        SimpleTable
    },
    data() {
        return {
            tableHeaders: [
                {
                    text: '',
                    value: 'color'
                },
                {
                    text: 'Name',
                    value: 'name'
                },
                {
                    text: 'Endpoint',
                    value: 'endpoint'
                },
                {
                    text: 'Collection',
                    value: 'collection'
                },
                {
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Location',
                    value: 'location'  
                },
                {
                    text: 'Unique Count',
                    value: 'uniqueCount'  
                },
                {
                  text: 'Public Count',
                  value: 'publicCount'
                }
            ],
            items: []
        }
    },
    methods: {
        computeLocation(isUrlParam, isHeader) {
            if (isHeader) {
                return "Request headers" 
            } else if (isUrlParam) {
                return "URL parameter"
            } else {
                return "Request Payload"
            }
        },
        fetchParamsStatus() {
            this.items = [];
            api.fetchParamsStatus().then((resp) => {
                let privateSingleTypeInfo = resp.privateSingleTypeInfo
                for (let idx =0; idx < privateSingleTypeInfo.length; idx++) {
                    let paramTypeInfo = privateSingleTypeInfo[idx]
                    let coll = this.apiCollections.find(x => x.id === paramTypeInfo.apiCollectionId);
                    this.items.push(
                        {
                            "name": paramTypeInfo.param,
                            "endpoint": paramTypeInfo.url,
                            "collection": coll.displayName,
                            "collectionId": coll.id,
                            "method": paramTypeInfo.method,
                            "location": this.computeLocation(paramTypeInfo.urlParam, paramTypeInfo.header),
                            "uniqueCount": paramTypeInfo.uniqueCount,
                            "publicCount": paramTypeInfo.publicCount,
                        }
                    )
                }
            })
        },
        rowClicked(row) {
            let urlAndMethod = row.endpoint + " " + row.method;
            let target = {
                name: 'apiCollection/urlAndMethod',
                params: {
                    apiCollectionId: row.collectionId,
                    urlAndMethod: btoa(urlAndMethod)
                }
            }
            this.$router.push(target)
        }
    },
    computed: {
        ...mapState('collections', ['apiCollections']),
    },
    mounted() {
        this.fetchParamsStatus();
    }
}

</script>

<style lang="sass">
.api-endpoints
    padding-right: 24px
    padding-left: 24px
    & .table-header
        vertical-align: bottom
        text-align: left
        padding: 12px 8px !important
        border: 1px solid var(--white) !important

    & .table-column
        padding: 4px 8px !important
        border-top: 1px solid var(--white) !important
        border-bottom: 1px solid var(--white) !important
        background: var(--themeColorDark18)
        color: var(--themeColorDark)
        max-width: 250px
        text-overflow: ellipsis
        overflow : hidden
        white-space: nowrap

        &:hover
            text-overflow: clip
            white-space: normal
            word-break: break-all


    .table-row
        border: 0px solid var(--white) !important
        position: relative

        &:hover
            background-color: var(--colTableBackground) !important

</style>