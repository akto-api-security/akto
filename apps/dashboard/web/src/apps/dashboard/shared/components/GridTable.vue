<template>
    <simple-table
        :headers="headers" 
        :items="items" 
        :class="['grid-table' , leftView ? 'left-view' : '']"
        @rowClicked="rowClicked"
    >
        <template v-for="(index, name) in $slots" v-slot:[name]>
            <slot :name="name" />
        </template>
        <template v-for="(index, name) in $scopedSlots" v-slot:[name]="data">
            <slot :name="name" v-bind="data"></slot>
        </template>
        <template v-slot:row-view="content">
            <server-table-block
                :item="content.rowData" 
                :index="content.index" 
                :currRowIndex="content.current" 
                :headers="headers" 
                @clickRow="rowClicked"
                :leftView="leftView"
            >
                <template v-for="(index, name) in $slots" v-slot:[name]>
                    <slot :name="name" />
                </template>
                <template v-for="(index, name) in $scopedSlots" v-slot:[name]="data">
                    <slot :name="name" v-bind="data"></slot>
                </template>
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
        leftView:obj.boolN,      
    },
    components:{
        SimpleTable,
        SensitiveChipGroup,
        ServerTableBlock
    },
    methods: {
        rowClicked(item,index){
            this.$emit('clickRow',item)
        }
    },
}
</script>

<style scoped>
    ::-webkit-scrollbar {
        display: none !important;
    }
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
    .left-view >>> .v-data-table{
        padding: 0px !important;
    }
    .left-view >>> tbody{
        gap: 4px !important;
        margin: 0px !important;
        padding: 4px 0px !important;
    }
    .left-view{
        max-height: 450px !important;
        overflow-y: scroll;
    }

</style>

