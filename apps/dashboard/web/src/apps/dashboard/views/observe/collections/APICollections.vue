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
            />
               <simple-text-field 
            :readOutsideClick="true"
            placeholder="New collection name"
            @changed="createCollection"
            class="add-collection-field"
            />

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
            actions: []
        }
    },
    methods: {
        rowClicked(item) {
            this.$emit("selectedItem", {type: 1, collectionName: item.name, apiCollectionId: item.id})
        },
        createCollection(name) {
            console.log(name);
          this.$store.dispatch('collections/createCollection', {name}).then(resp => {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${name} ` +`added successfully!`,
                color: 'green'
            })
          })
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
    .add-collection-field
        width: 350px
        padding: 32px
</style>