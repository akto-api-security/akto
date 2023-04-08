<template>
    <tr
        :class="['table-row', index == currRowIndex ? 'highlight-row' : '']"
    >
        <td
            class="table-column"
            :style="{'background-color':item.color, 'padding' : '0px !important', 'width': item.width}"
        />
        <td 
            v-for="(header, ii) in headers.slice(1)"
            :key="ii"
            class="table-column clickable"
            @click="clickRow(item)"
        >
            <slot :name="[`item.${header.value}`]" :item="item">
                <div class="table-entry">{{item[header.value]}}</div>
            </slot>
        </td>
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
}
.table-column:first-child , .table-column:nth-child(4){
    display:none;
}

.table-column:nth-child(3){
    font-weight: 600;
}

.table-column:nth-child(2) {
    height: 60px !important;
    text-overflow: clip;
    white-space: normal;
    word-break: break-all;
}

.table-column{
    padding: 4px 8px !important ;
    border-bottom: none !important;
    color: var(--themeColorDark);
}
.highlight-row{
}
</style>