<template>
    <tr
        :class="['table-row', index == currRowIndex ? 'highlight-row' : '' , leftView ? 'left-view-table' : '']"
    >
        <td 
            v-for="(header, ii) in headers"
            :key="ii"
            class="table-column clickable"
            @click="clickRow(item,index)"
        >
            <slot :name="[`item.${header.value}`]" :item="item">
                <div class="table-entry">{{item[header.value]}}</div>
            </slot>
        </td>
        <slot name="test-categories" />
    </tr>
</template>

<script>

import obj from "@/util/obj"
export default {
    name:"ServerTableBlock",
    props:{
        item:obj.objR,
        actions:obj.arrN,
        headers: obj.arrR,
        index:obj.numR,
        currRowIndex:obj.numR,
        leftView:obj.boolN,
    },
    methods:{
        clickRow(item){
            this.$emit('clickRow',item)
        }
    }
}
</script>

<style lang="scss" scoped>
.table-row {
    display: flex;
    flex-direction: column;
    height: 180px;
    width: 310px;
    box-sizing: border-box;
    border: 1px solid;
    border-radius: 12px;
    padding: 16px;
    background: var(--white);
    gap: 4px;
}
.table-column:first-child {
    text-overflow: clip;
    white-space: normal;
    word-break: break-all;
    font-weight: 500;
    .table-entry{
        font-size: 20px !important;
    }
}
.table-column:nth-child(2){
    height: 30px !important;
    .table-entry{
        font-size: 14px !important;
    }
}
.table-column:nth-child(3){
    order: 99;
}

.table-column{
    padding: 0px 8px !important ;
    border-bottom: none !important;
    color: var(--themeColorDark);
}
.highlight-row{
    background: var(--hexColor27);
}
.left-view-table{
    height: 73px !important;
    width: 210px !important;
    border-radius: 8px !important;
    border: 1px solid var(--white); 
    .table-column:nth-child(3){
        display: none;
    }
    .table-column:first-child {
        height: 21px !important;
        .table-entry{
            font-size: 16px !important;
        }
    }
    .table-column:nth-child(2){
        height: 16px !important;
        .table-entry{
            font-size: 12px !important;
        }
    }
}
</style>