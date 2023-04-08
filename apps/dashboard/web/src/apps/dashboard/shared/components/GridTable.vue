<template>
    <simple-table
        :headers="headers" 
        :items="items" 
        name="" 
        sortKeyDefault="vulnerable" 
        :pageSize="10"
        :sortDescDefault="true"
        class="grid-table"
    >
        <template v-slot:row-view="content">
            <server-table-block
                :item="content.rowData" 
                :index="content.index" 
                :currRowIndex="content.current" 
                :headers="headers" 
                @clickRow="rowClicked"
            >
            </server-table-block>
        </template>
    </simple-table>
</template>

<script>

import obj from "@/util/obj"
import SimpleTable from './SimpleTable.vue'
import SensitiveChipGroup from './SensitiveChipGroup.vue'
import ServerTableBlock from './rows/ServerTableBlock.vue'

export default {
    name: "GridTable",
    props:{
        headers: obj.arrR,
        items: obj.arrR,      
    },
    components:{
        SimpleTable,
        SensitiveChipGroup,
        ServerTableBlock
    },
    methods: {
        rowClicked(item){
            this.$emit('clickRow',item)
        }
    },
}
</script>

<style scoped>
    .grid-table >>> tbody{
        display: flex !important;
        flex-wrap: wrap;
        gap: 24px;
        margin: 24px 0px;
        padding: 5px;
    }

    .grid-table >>> th{
        display: none;
    }
</style>

