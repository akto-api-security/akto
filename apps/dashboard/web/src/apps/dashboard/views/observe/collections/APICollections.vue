<template>
    <div>   
        <spinner v-if="loading" />      
        <div v-else class="pt-8">
            <simple-table
                :headers="headers" 
                :items="apiCollectionsForTable"  
                :actions="actions"
                name="API Collections" 
                sortKeyDefault="name" 
                :sortDescDefault="false"   
                @rowClicked=rowClicked
                hide-default-footer ="true"
            >
                <template v-slot:add-new-row-btn="{}">
                    <div class="clickable download-csv ma-1">
                        <v-btn icon :disabled=showNewRow :color="$vuetify.theme.themes.dark.themeColor"  @click="showNewRow = true">
                            <v-icon>$fas_plus</v-icon>
                        </v-btn>
                    </div>            
                </template>
                <template v-slot:add-new-row="{}">
                    <template><td/><td>
                    <simple-text-field 
                        :readOutsideClick="true"
                        placeholder="New collection name"
                        @changed="createCollection"
                        @aborted="showNewRow = false"
                        v-if="showNewRow"
                    /></td></template>
                </template>
            </simple-table>

        </div>
    </div>        
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import { mapState } from 'vuex'
import func from '@/util/func'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'

export default {
    name: "ApiCollections",
    components: { 
        SimpleTable,
        Spinner,
        SimpleTextField
    },
    
    data() {
        return { 
            headers: [
                {
                    text: "",
                    value: "color"
                },
                {
                    text: "API Name",
                    value: "name"
                },
                {
                    text: "Endpoints",
                    value: "endpoints"
                },
                {
                    text: "Discovered",
                    value: "detected"
                }
            ],
            actions: [],
            showNewRow: false
        }
    },
    methods: {
        rowClicked(item) {
            this.$emit("selectedItem", {type: 1, collectionName: item.name, apiCollectionId: item.id})
        },
        createCollection(name) {
          this.$store.dispatch('collections/createCollection', {name})
          this.showNewRow = false
        }

    },
    computed: {
        ...mapState('collections', ['apiCollections', 'loading']),
        apiCollectionsForTable() {
            return this.apiCollections.map(c => {
                return {
                    ...c,
                    color: "#FFFFFF",
                    endpoints: (c.urls || []).length,
                    detected: func.prettifyEpoch(c.startTs)
                }
            })
        }
    },
    mounted () {
        this.$emit('mountedView', {type: 0})
    }
}
</script>

<style lang="sass" scoped>
    .default-info
        color: #47466A
        font-size: 12px
        margin-top: 16px
</style>