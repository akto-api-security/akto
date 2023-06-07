<template>
    <simple-table
        :headers="headers" 
        :items="items" 
        name="" 
        :pageSize="20"
        :sortDescDefault="true"
        :hideDownloadCSVIcon="true"
        class="github-table"
    >
        <!-- TODO: handle sorting and severity -->
        <template v-slot:add-user-headers="{totalItems,getColumnValueList,appliedFilter,setSortOrInvertOrder}">
            <div class="d-flex jc-sb github-header-container" :style="{'width': '100%'}">
                <span class="text">
                    {{ totalItems }} Test Runs
                </span>
                <div class="d-flex" :style="{gap:'8px'}">
                    <template v-for="(header) in headers">
                        <simple-menu :items="computeActionItems(getColumnValueList(header.value))" 
                            :key="header.text" v-if="header.showFilterMenu" :newView="true" :title="header.text"
                            @menuClicked="appliedFilter(header.value,itemClicked($event))"
                        >
                            <template v-slot:activator2>
                                <div class="d-flex align-center">
                                    <span class="text">{{ header.text }}</span>
                                    <v-icon :size="12" color="var(--themeColorDark)">$fas_angle-down</v-icon>  
                                </div>  
                            </template>
                        </simple-menu>
                    </template>
                    <simple-menu :newView="true" :items="sortHeaders" title="Sort By" 
                            @menuClicked="setSortOrInvertOrder(itemClicked($event))"
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
                            <slot :name="[`item.${header.value}`]" :item="content.rowData[header.value]">    
                                <div class="row-items" :key="index" 
                                    v-if="header.row_order == 1 && content.rowData[header.value] && content.rowData[header.value].length > 0"
                                >
                                    <v-icon :size=16 v-if="header.value === 'icon'" 
                                            :color="computeIcon(content.rowData[header.value],1)"
                                    > 
                                        {{ computeIcon(content.rowData[header.value] , 0) }}
                                    </v-icon>
                                    <div class="title" v-else-if="isValid(header)">{{ content.rowData[header.value] }}</div>
                                    <div class="box_container" :style="getStyles(header.color)" v-else>
                                        {{ content.rowData[header.value] }}
                                    </div>
                                </div>
                            </slot>
                        </template>
                    </div>
                    <div class="second-row">
                        <template v-for="(header,index) in headers">
                            <slot :name="[`item.${header.value}`]" :item="content.rowData[header.value]">
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
                                <v-btn icon :ripple="false" @click="() => content.current = content.index">
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
    },
    components:{
        SimpleTable,
        SimpleMenu
    },
    methods: {
        rowClicked(item){
            this.$emit('clickRow',item)
        },
        computeIcon(iconStr,index){
            let iconArr = iconStr.split("/")
            return iconArr[index]
        },
        isValid(header){
            return !(header.value.toString().includes('severity'))
        },
        getStyles(colorStr){
            let colorArr = colorStr.split("/")
            return{
                background: colorArr[1],
                color: colorArr[0]
            }
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
        itemClicked(item){
            return item
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
.github-table >>> .v-data-table__wrapper{
    border: 1px solid var(--themeColorDark16) !important;
}
.github-table >>> .github-table-row{
    display: flex !important;
    flex-direction: column !important;
    padding: 12px 24px !important;
    border-bottom: 1px solid var(--themeColorDark16) !important;
}
.github-table >>> .v-data-footer,.text{
    color:var(--themeColorDark);
    font-size: 14px;
    font-weight: 500;
}
</style>
<style scoped lang="scss">
    .github-header-container{
        padding: 16px 24px;
        width: 100%;
        background: var(--themeColorDark17);
        border-radius: 6px 6px 0px 0px;
        border: 1px solid var(--themeColorDark16);
        margin-right: -8px;
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
        .title{
            color: var(--themeColorDark);
            font-weight: 600;
            font-size: 16px;
        }
        .box_container {
            font-size: 12px;
            padding: 2px 8px;
            border-radius: 16px;
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

