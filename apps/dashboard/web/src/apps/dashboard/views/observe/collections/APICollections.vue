<template>
    <div>   
        <spinner v-if="loading" />      
        <simple-table
            v-else
            :headers="headers" 
            :items="apiCollectionsForTable"  
            :actions="actions"
            name="API Collections" 
            sortKeyDefault="name" 
            :sortDescDefault="false"   
            @rowClicked=rowClicked     
        />
    </div>        
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import { mapState } from 'vuex'
import func from '@/util/func'
import Spinner from '@/apps/dashboard/shared/components/Spinner'

export default {
    name: "ApiCollections",
    components: { 
        SimpleTable,
        Spinner
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