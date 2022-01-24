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
            actions: [ 
                {
                    isValid: item => this.isValid(item),
                    icon: item => '$fas_trash',
                    text: item => 'Delete Collection',
                    func: item => this.deleteCollection(item),
                    success: (resp, item) => this.successfullyDeleted(resp, item),
                    failure: (err, item) => this.unsuccessfullyDeleted(err, item)
                }
            ],
            showNewRow: false,
            deletedCollection: null
        }
    },
    methods: {
        rowClicked(item) {
            this.$emit("selectedItem", {type: 1, collectionName: item.name, apiCollectionId: item.id})
        },
        createCollection(name) {
          this.$store.dispatch('collections/createCollection', {name}).then(resp => {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${name} ` +`added successfully!`,
                color: 'green'
            })
          })
          this.showNewRow = false
        },
        deleteCollection(item) {
            this.deletedCollection = item.name
            if(confirm("Are you sure you want to delete this collection?")) {
                const summ = this.$store.dispatch('collections/deleteCollection', {apiCollection: item})
                console.log(summ)
                return summ
            }
        },
        successfullyDeleted(resp,item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${this.deletedCollection}` + ` deleted successfully!`,
                color: 'green'
            })
            this.deletedCollection = null
        },
        unsuccessfullyDeleted(resp,item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${this.deletedCollection}` + ` could not be deleted`,
                color: 'red'
            })
            this.deletedCollection = null
        },
        isValid(item) {
            if(item.id != 0)
                return true;
            else
                return false;
        }  
    },
    computed: {
        ...mapState('collections', ['apiCollections', 'loading']),
        apiCollectionsForTable () {
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