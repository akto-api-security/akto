<template>
    <simple-table
        :headers="headers" 
        :items="items" 
        name="" 
        :pageSize="20"
        :sortDescDefault="true"
        :sortKeyDefault="sortKeyDefault"
        :hideDownloadCSVIcon="true"
        class="github-table"
        @rowClicked = "rowclick"
    >
        <!-- TODO: handle sorting and severity -->
        <template v-slot:add-user-headers="{totalItems,getColumnValueList,appliedFilter,setSortOrInvertOrder,clearFilters,filters}">
                <v-btn  :ripple="false" plain color="var(--themeColorDark3)" v-if="isFilter(filters)" @click="handleFilters(clearFilters)" class="pa-0 btn-clear">
                    <v-icon :size="14">$fas_times</v-icon>
                    Clear filters
                </v-btn>
            <div class="d-flex jc-sb github-header-container" :style="{'width': '100%'}">
                <span class="text">
                    {{ totalItems }} Test {{ computeRunsText(totalItems) }}
                </span>
                <div class="d-flex" :style="{gap:'8px'}">
                    <template v-for="(header) in headers">
                        <simple-menu :items="computeActionItems(getColumnValueList(header.value))" 
                            :key="header.text" v-if="header.showFilterMenu && computeActionItems(getColumnValueList(header.value)).length>0" :newView="true" :title="ComputeTitle(header.text)"
                            @menuClicked="appliedFilter(header.value,itemClicked($event))"
                            :clear-filters-value="clearFilterValue"
                        >
                            <template v-slot:activator2>
                                <div class="d-flex align-center">
                                    <span class="text">{{ header.text }}</span>
                                    <v-icon :size="12" color="var(--themeColorDark)">$fas_angle-down</v-icon>  
                                </div>  
                            </template>
                        </simple-menu>
                    </template>
                    <simple-menu v-if="items.length>0" :newView="true" :items="sortHeaders" title="Sort By" 
                            @menuClicked="setSortOrInvertOrder(itemClicked($event))" :select-exactly-one="true"
                            :clear-filters-value="clearFilterValue"
                    >
                        <template v-slot:activator2>
                            <div class="d-flex align-center">
                                <span class="text">Sort</span>
                                <v-icon :size="12" color="var(--themeColorDark)">$fas_angle-down</v-icon>  
                            </div>  
                        </template>
                    </simple-menu>
                </div>
            </div>
        </template>
        <template v-slot:row-view="content">
            <slot name="custom-github-row" :rowData="content">
                <tr
                    :class="['github-table-row', content.index == content.current ? 'highlight-row' : '']"
                >
                    <div class="first-row">
                        <template v-for="(header,index) in headers">
                            <slot :name="[`${header.value}_1`]" :item="content.rowData[header.value]">    
                                <div class="row-items" :key="index" 
                                    v-if="header.row_order == 1 && content.rowData[header.value] && content.rowData[header.value].length > 0"
                                >
                                    <v-icon :size=16 v-if="header.value === 'icon'" 
                                            :color="computeIcon(content.rowData[header.value],1)"
                                    > 
                                        {{ computeIcon(content.rowData[header.value] , 0) }}
                                    </v-icon>
                                    <div class="main-title" v-else>{{ content.rowData[header.value] }}</div>
                                </div>
                            </slot>
                        </template>
                    </div>
                    <div class="second-row">
                        <template v-for="(header,index) in headers">
                            <slot :name="[`${header.value}_2`]" :item="content.rowData[header.value]">
                                <div class="row-items" v-if="header.row_order == 2" :key=index>
                                    <v-icon :size="14" color="var(--themeColorDark3)">{{ header.icon }}</v-icon>
                                    <span class="row-text">{{ content.rowData[header.value] }}</span>
                                    <v-divider class="divider" />
                                </div>
                            </slot>
                        </template>
                    </div>
                    <div v-if="actions && actions.length > 0" class="table-row-actions">
                        <simple-menu :items="actions" :newView="true">
                            <template v-slot:activator2>
                                <v-btn icon :ripple="false" @click="$emit('actionsClicked',content.rowData)">
                                    <v-icon size="14" color="var(--themeColorDark)">$fas_ellipsis-h</v-icon>
                                </v-btn>                    
                            </template>
                        </simple-menu>
                    </div>
                </tr>
            </slot>
        </template>
    </simple-table>
</template>

<script>

import obj from "@/util/obj"
import SimpleTable from './SimpleTable.vue'
import SimpleMenu from './SimpleMenu.vue'

export default {
    name: "GridTable",
    props:{
        headers: obj.arrR,
        items: obj.arrR,  
        actions: obj.arrN,  
        sortKeyDefault: obj.strN,  
    },
    components:{
        SimpleTable,
        SimpleMenu
    },
    data(){
        return{
            clearFilterValue: false,
        }
    },
    methods: {
        rowClicked(item){
            this.$emit('clickRow',item)
        },
        handleFilters(clearFilters){
            clearFilters()
            this.clearFilterValue = true
        },
        computeIcon(iconStr,index){
            let iconArr = iconStr.split("/")
            return iconArr[index]
        },
        isValid(header){
            return !(header.value.toString().includes('severity'))
        },
        computeActionItems(headerObj){
            let arr = []
            headerObj.values.forEach((x) =>{
                let obj = {
                    label: x.title,
                    value: x.value,
                    isValid:true,
                }
                arr.push(obj)
            })
            return arr
        },
        ComputeTitle(text){
            return "Filter by " + text.toLowerCase()
        },
        itemClicked(item){
            this.clearFilterValue = false
            if(item.item.header?.showSort){
                return item.item
            }
            return item
        },
        isFilter(filters){
            let val = false
            this.headers.forEach((header)=>{
                if(filters[header.sortKey || header.value] && filters[header.sortKey || header.value].size > 0){
                    val= true
                }
            })
            return val
        },
        computeRunsText(totalItems){
            if(totalItems==1){
                return "run";
            }
            return "runs";
        },
        rowclick(item){
            console.log("row click action", item);
        }
    },
    computed:{
        sortHeaders(){
            let arr = []
            this.headers.forEach((header) =>{
                if(header.showSort){
                    if(header.sortText){
                        let sortArr = header.sortText.split("/")
                        for(let index = 0 ; index < sortArr.length ; index += 2){
                            let order = true
                            if(sortArr[index + 1] != '1'){
                                order = false
                            }
                            let obj={
                                label: sortArr[index],
                                header: header,
                                sortOrder: order,
                                isValid:true,
                            }
                            arr.push(obj)
                        }
                    }else{
                        let obj = {
                            label: header.title,
                            header: header,
                            sortOrder: true,
                            isValid:true,
                        }
                        arr.push(obj)
                    }
                }
            })
            return arr
        }
    }
}
</script>
<style scoped>
.github-table >>> th{
    display: none;
}
.github-table >>> tr:hover{
    background-color:  var(--themeColorDark20) !important;
}
.github-table >>> .v-data-table__wrapper{
    border: 1px solid var(--themeColorDark16) !important;
    border-radius: 0px 0px 12px 12px;
}
.github-table >>> .github-table-row{
    display: flex !important;
    flex-direction: column !important;
    padding: 13px 24px !important;
    border-bottom: 1px solid var(--themeColorDark16) !important;
}

.github-table>>>.github-table-row:last-child{
        border-bottom:none !important;
}

.github-table >>> .v-data-footer,.text{
    color:var(--themeColorDark);
    font-size: 14px;
    font-weight: 500;
    border-top: none;
}
.github-table{
    margin-top: 32px;
}
</style>
<style scoped lang="scss">
    .github-header-container{
        padding: 20px 24px;
        width: 100%;
        background: var(--themeColorDark22);
        border-radius: 12px 12px 0px 0px;
        border: 1px solid var(--themeColorDark16);
        margin-top: 10px;
        border-bottom: none;
        .btn-clear{
            height: 18px !important;
        }
    }
    .table-row-actions{
        position: absolute;
        right: 40px;
        opacity: 1;
        margin-top: 9px;
    }
    .highlight-row{
        background-color: var(--themeColorDark18);
    }
    .first-row{
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 8px;
        .main-title{
            color: var(--themeColorDark);
            font-weight: 600;
            font-size: 16px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 50vw;
        }
    }
    .second-row{
        display: flex;
        padding-left: 30px;
        .row-items {
            display: flex;
            align-items: center;
            margin: 0 !important;
            gap: 1px;
            .row-text{
                font-size: 12px;
                color: var(--themeColorDark3);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
                max-width: 20vw;
                font-weight: 400;
            }
            .divider{
                border-width: 2px !important;
                border-color: var(--themeColorDark3) !important;
                border-radius: 50% !important;
                margin: 0 8px;
            }
        }
    }
    .second-row:nth-child(2)>:last-child>:last-child{
        display: none;
    }
</style>

