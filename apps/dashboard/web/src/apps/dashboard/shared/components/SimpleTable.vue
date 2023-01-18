<template>
    <div>        
        <v-data-table
            :headers="headers"
            :items="filteredItems"
            class="board-table-cards keep-scrolling"
            :search="search"
            :sort-by="sortKey"
            :sort-desc="sortDesc"
            :custom-sort="sortFunc"
            :items-per-page.sync="itemsPerPage"
            @current-items="storeCurrentItems"
            hide-default-footer
            :page.sync="pageNum"
            hide-default-header
            :fillInitial="fillInitial"
        >
            <template v-slot:top="{ pagination, options, updateOptions }" v-if="items && items.length > 0">
                <div class="headerContainer">
                    <div class="headerDiv">
                        <div v-if="showName" class="table-name">
                            {{name}}
                        </div>                 
                        <div>
                            <slot name="massActions"/>
                        </div>

                        <v-menu offset-y :close-on-content-click="false" v-if="headers.length > 4">
                            <template v-slot:activator="{ on, attrs }">
                                <v-btn class = "showEnd" v-bind= "attrs" v-on = "on" plain>
                                    <span class="filterHeaderSpan">
                                        More Filters
                                    </span>
                                </v-btn>     
                            </template>

                            <filter-list
                                :title="headers[1].text"
                                hideOperators
                                hideListTitle
                                :items="convertHeadersList()"
                                @clickedItem = "pushIntoNew($event)"
                                @selectedAll = "selectAllHeaders($event)"
                            />
                        
                        </v-menu>

                        <template v-for = "(header,index) in selectedHeaders">
                            <span>
                                <v-menu :key="index" offset-y :close-on-content-click="false" v-model="showFilterMenu[header.value]"> 
                                    <template v-slot:activator="{ on, attrs }">
                                        <v-btn class = "showButtons" v-bind = "attrs" v-on = "on">
                                            <span class="filterHeaderSpan">
                                                {{header.text}}
                                                <v-icon :size="14">$fas_angle-down</v-icon>
                                            </span>
                                        </v-btn>     
                                    </template>
                                    <filter-list 
                                        :title="header.text" 
                                        :items="columnValueList[header.value]"                                         
                                        @clickedItem="appliedFilter(header.value, $event)" 
                                        @operatorChanged="operatorChanged(header.value, $event)"
                                        @selectedAll="selectedAll(header.value, $event)"
                                    />
                                </v-menu>
                            </span> 
                        
                        </template>
                    </div>

                    <div class="headerDiv">

                        <v-btn-toggle
                            v-model="toggle_exclusive"
                            mandatory
                        >
                            <template v-for="(item,index) in arrayItems">
                                <v-tooltip bottom :key="index">
                                    <template v-slot:activator="{on, attrs}">
                                        <v-btn 
                                            v-on="on"
                                            v-bind="attrs" 
                                            :value = item.value
                                            icon
                                            plain
                                        >
                                            <v-icon>{{item.icon}}</v-icon>
                                        </v-btn>
                                    </template>
                                    {{item.toolTipText}}
                                </v-tooltip>
                            </template>
                        </v-btn-toggle>

                        <span>
                            <v-menu offset-y>
                                <template v-slot:activator="{ on, attrs }">
                                    <v-btn
                                    dark
                                    text
                                    color="primary"
                                    v-bind="attrs"
                                    v-on="on"
                                    >
                                    <span v-if ="itemsPerPage > 0">
                                        {{ itemsPerPage }}
                                    </span>

                                    <span v-else>
                                        Choose Rows Per Page
                                    </span>
                                        <v-icon :size="14">$fas_angle-down</v-icon>
                                    </v-btn>
                                </template>
                                <v-list>
                                    <v-list-item
                                    v-for="(val, index) in itemsPerPageArray"
                                    :key="index"
                                    @click="updateItemsPerPage(val)"
                                    >
                                        <v-list-item-title v-if="val > 0">{{ val }}</v-list-item-title>
                                        <v-list-item-title v-else>Show All</v-list-item-title>
                                    </v-list-item>
                                </v-list>
                            </v-menu>
                        </span>

                        <v-data-footer 
                            v-if="enablePagination && itemsPerPage > 0"
                            :pagination="pagination" 
                            :options="options"
                            @update:options="updateOptions"
                            prevIcon= '$fas_angle-left'
                            nextIcon= '$fas_angle-right'
                            show-current-page
                        />

                        <div class="d-flex board-table-cards jc-end">
                            <div class="clickable download-csv ma-1">
                                <v-tooltip bottom>
                                    <template v-slot:activator="{on, attrs}">
                                        <v-btn 
                                            v-on="on"
                                            v-bind="attrs" 
                                            class="showButtons"
                                            @click="downloadData" v-if="!hideDownloadCSVIcon">
                                            Export
                                        </v-btn>
                                    </template>
                                    Download as CSV
                                </v-tooltip>
                            </div>
                            <slot name="add-new-row-btn" :filteredItems=filteredItems />
                        </div>
                    </div>
                </div>
            </template>

            <template v-slot:footer.prepend="{}">
                <v-spacer/>
            </template>

            <template v-slot:header="{}">
                <template v-for="(header, index) in headers">
                    <th
                            class='table-header'
                            :style="index == 0 ? {'padding': '2px !important'} : {}" 
                            :key="index"
                    >
                        <div v-if="index > 0">
                                <span class="table-sub-header">
                                    <span class="clickable"  @click="setSortOrInvertOrder(header)">
                                        {{header.text}} 
                                    </span>                                    
                                </span>
                            
                        </div>
                    </th>
                </template>
                <template>
                        <tr class="table-row" >
                            <slot name="add-new-row" />
                        </tr>                
                </template>

            </template>
            <template v-slot:item="{item}">
                <tr class="table-row" 
                    @click="rowSelected(item)"
                    :class="currentRow.indexOf(item) > -1 ? 'grey' : ''"
                >
                    <td
                        class="table-column high-dense"
                        :style="{'background-color':item.color, 'padding' : '0px !important'}"
                    />
                    <td
                        v-for="(header, index) in headers.slice(1)"
                        :key="index"
                        class="table-column clickable"
                        @click="$emit('rowClicked', item)"
                        :style="adjustHeight"
                    >
                        <slot :name="[`item.${header.value}`]" :item="item">
                            <div class="table-entry">{{item[header.value]}}</div>
                        </slot>
                    </td>

                    <td v-if="actions && actions.length > 0" class="table-column" :style="adjustHeight">
                        <simple-menu :items="actionsFunction(item)">
                            <template v-slot:activator2>
                                <v-icon
                                    size=16
                                    color="#424242"
                                >
                                    $fas_ellipsis-v
                                </v-icon>
                            </template>
                        </simple-menu>
                    </td>
                </tr>
            </template>
        </v-data-table>
    </div>
</template>

<script>

import obj from "@/util/obj"
import { saveAs } from 'file-saver'
import FilterList from './FilterList'
import SimpleMenu from './SimpleMenu.vue'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField.vue'


export default {
    name: "SimpleTable",
    components: {
        SimpleMenu,
        FilterList,
        SimpleTextField,
    },
    props: {
        headers: obj.arrR,
        items: obj.arrR,
        name: obj.strN,
        sortKeyDefault: obj.strN,
        sortDescDefault: obj.boolN,
        actions: obj.arrN,
        allowNewRow: obj.boolN,
        hideDownloadCSVIcon: obj.boolN,
        showName: obj.boolN,
    },
    data () {
        return {
            pageNum:1,
            initialDisplayedItems:[],
            selectedHeaders: [],
            currentRow: [],
            itemsPerPage: 20,
            itemsPerPageArray:[5,10,15,20,25,30,-1],
            search: null,
            sortKey: this.sortKeyDefault || null,
            sortDesc: this.sortDescDefault || false,
            filters: this.headers.reduce((map, e) => {map[e.value] = new Set(); return map}, {}),
            showFilterMenu: this.headers.reduce((map, e) => {map[e.value] = false; return map}, {}),
            filterOperators: this.headers.reduce((map, e) => {map[e.value] = 'OR'; return map}, {}),
            toggle_exclusive: 48,
            arrayItems:[
                {
                    value: 48,
                    icon: '$fas_grip-lines',
                    toolTipText:'Default'
                },
                {
                    value: 36,
                    icon: '$fas_bars',
                    toolTipText:'Comfortable'
                },
                {
                    value: 24,
                    icon: '$fas_align-justify',
                    toolTipText:'Compact'
                }
            ],
        }
    },
    methods: {
        storeCurrentItems(items){
            this.initialDisplayedItems = items
        },
        selectRow(index){
            this.rowSelected(this.initialDisplayedItems[index])
            this.$emit('rowClicked',this.initialDisplayedItems[index])
        },
        moveRowsOnKeys(event){
            if(event.keyCode === 40){
                if(this.currentRow.length > 0)
                {
                    var index = this.initialDisplayedItems.indexOf(this.currentRow[0])
                    var n = this.initialDisplayedItems.length - 1
                    if(index === n){
                        this.pageNum++
                        this.selectRow(0)
                    }

                    else{
                        index++
                        this.selectRow(index)
                    }
                }
                else{
                    this.selectRow(0)
                }
            }

            else if(event.keyCode === 38){
                if(this.currentRow.length > 0)
                {
                    var index = this.initialDisplayedItems.indexOf(this.currentRow[0])
                    if(index === 0){
                        if(this.pageNum > 1){
                            this.pageNum--
                            this.selectRow((this.initialDisplayedItems.length - 1))
                        }
                    }
                    else{
                        if(index === -1){
                            this.selectRow((this.initialDisplayedItems.length - 1))
                        }

                        else{
                            index--
                            this.selectRow(index)
                        }
                    }
                }

                else{
                    this.selectRow(0)
                }
            }

            else if(event.keyCode === 39){
                var totalPages = Math.ceil((this.filteredItems.length / this.itemsPerPage))
                if(this.pageNum < totalPages){
                    this.pageNum++
                }
            }

            else if(event.keyCode === 37){
                var totalPages = Math.ceil((this.filteredItems.length / this.itemsPerPage))
                if(this.pageNum > 1){
                    this.pageNum--
                }
            }
        },
        rowSelected(item){
            this.currentRow = []
            this.currentRow.push(item)
        },
        updateItemsPerPage (value) {
            this.itemsPerPage = value
        },
        actionsFunction(item){
            let arrayActions = []
            this.actions.forEach(action => {
                if(action.isValid(item)){
                    arrayActions.push({label:action.text(item) ,icon:action.icon(item), click:action.func})
                }
            })

            return arrayActions
        },
        convertHeadersList(){
            let headerItems = []

            let ind = 4
            if(this.headers[0].value == 'color'){
                ind++
            }

            while(ind < this.headers.length){
                headerItems.push({title:this.headers[ind].text, value:this.headers[ind].value})
                ind++
            }
            return headerItems

        },

        selectAllHeaders({checked}){
            if(checked){
                this.selectedHeaders = []
                this.headers.forEach(element =>{
                    if(element.value !== 'color'){
                        this.selectedHeaders.push({text:element.text, value: element.value})
                    }
                })
            }

            else{
                this.selectedHeaders = []
            }
        },

        pushIntoNew({item , checked}){
            if(checked){
                this.selectedHeaders.push({text:item.title,value:item.value})
            }

            else{
                var index = 0 ;
                while(index < this.selectedHeaders.length){
                    if(this.selectedHeaders[index].text === item.title){
                        this.filters[item.value].clear()
                        this.filterOperators[item.value] = 'OR'
                        this.filters = {...this.filters}
                        this.selectedHeaders.splice(index , 1)
                    }else{
                        index++
                    }
                }
            }
        },
        selectedAll (hValue, {items, checked}) {
            for(var index in items) {
                if (checked) {
                    this.filters[hValue].add(items[index].value)
                } else {
                    this.filters[hValue].delete(items[index].value)
                }
            }
            this.filters = {...this.filters}

        },
        appliedFilter (hValue, {item, checked, operator}) { 
            this.filterOperators[hValue] = operator || 'OR'
            if (checked) {
                this.filters[hValue].add(item.value)
            } else {
                this.filters[hValue].delete(item.value)
            }
            this.filters = {...this.filters}
        },
        operatorChanged(hValue, {operator}) {
            this.filterOperators[hValue] = operator || 'OR'
        },
        valToString(val) {
            if (val instanceof Set) {
                return [...val].join(" & ")
            } else {
                return val || "-"
            }
        },
        downloadData() {
            let headerTextToValueMap = Object.fromEntries(this.headers.map(x => [x.text, x.value]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",")+"\r\n"
            this.filteredItems.forEach(i => {
                csv += Object.values(headerTextToValueMap).map(h => this.valToString(i[h])).join(",") + "\r\n"
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, (this.name || "file") + ".csv");
        },
        sortFunc(items, columnToSort, isDesc) {

            if (!items || items.length == 0) {
                return items
            }

            if (!columnToSort || columnToSort === '') {
                return
            }

            let sortKey = this.headers.find(x => x.value === columnToSort)
            if (sortKey) {
                columnToSort = sortKey
            }

            let ret = items.sort((a, b) => {
                
                let ret = a[columnToSort] > b[columnToSort] ? 1 : -1
                if (isDesc[0]) {
                    ret = -ret
                }
                return ret
            })

            return ret
        },
        setSortOrInvertOrder (header) {
            let headerSortKey = header.sortKey || header.value
            if (this.sortKey === headerSortKey) {
                this.sortDesc = !this.sortDesc
            } else {
                this.sortKey = headerSortKey
            }
            // return this.sortFunc(this.items, this.sortKey, this.sortDesc)
        },
        filterFunc(item, header) {
            let itemValue = item[header]
            let selectedValues = this.filters[header]
            if (this.filters[header].size < 1) {
                return true
            } 
            
            if (itemValue instanceof Array) {
                itemValue = new Set(itemValue);
            }

            if(itemValue instanceof Set) {
                switch(this.filterOperators[header]) {
                    case "OR":
                        return [...selectedValues].filter( v => itemValue.has(v)).length > 0 
                    case "AND":
                        return [...selectedValues].filter( v => !itemValue.has(v)).length == 0 
                    case "NOT":
                        return [...selectedValues].filter( v => itemValue.has(v)).length == 0 

                }
            } else {
                switch (this.filterOperators[header]) {
                    case "OR": 
                    case "AND":
                        return selectedValues.has(itemValue)
                    case "NOT":
                        return !selectedValues.has(itemValue)
                }
            }
        },
        
    },
    mounted() {
        window.addEventListener('keydown',this.moveRowsOnKeys,null)
    },
    computed: {

        columnValueList: {
            get () {
                return this.headers.reduce((m, h) => {
                    let allItemValues = []
                    
                    this.items.forEach(i => {
                            let value = i[h.value]
                            if (value instanceof Set) {
                                allItemValues = allItemValues.concat(...value)
                            } else if (value instanceof Array) {
                                allItemValues = allItemValues.concat(...value)
                            } else if (typeof value !== 'undefined') {
                                return allItemValues.push(i[h.value])
                            }
                        }
                    )

                    let distinctItems = [...new Set(allItemValues.sort())]
                    m[h.value] = distinctItems.map(x => {return {title: x, subtitle: '', value: x}})
                    return m
                }, {})
            }
        },
        filteredItems() {
            return this.items.filter((d) => {
                return Object.keys(this.filters).every((f) => {
                return this.filterFunc(d, f)
                });
            });
        },
        enablePagination() {
            return this.filteredItems && this.filteredItems.length > this.itemsPerPage
        } ,
        fillInitial(){
            if(this.selectedHeaders.length < 1){
                var index = 0
                var lim = 4
                if(this.headers[0].value == 'color'){
                    index++
                    lim++
                }
                while(index < lim && index < this.headers.length){
                    this.selectedHeaders.push({text:this.headers[index].text, value:this.headers[index].value})
                    index++
                }
            }
        },
        adjustHeight: function(){
            var height = this.toggle_exclusive
            
            return{
                height: height + 'px'
            }
        }
    },
}
</script>

<style scoped>
    .board-table-cards >>> tbody tr:first-child td:first-child {
        border-radius: 2px 0 0 0;
    }

    .board-table-cards >>> tbody tr:first-child td:last-child {
        border-radius: 0 2px 0 0;
    }

    .board-table-cards >>> tbody tr:last-child td:last-child {
        border-radius: 0 0 2px 0;
    }

    .board-table-cards >>> tbody tr:last-child td:first-child {
        border-radius: 0 0 0 2px;
    }
</style>

<style lang="scss">

    .high-dense{
        min-height: 0px !important;
        height: 24px !important;
    }

    .keep-scrolling {
        /* Hide scrollbar for Chrome, Safari and Opera */
        ::-webkit-scrollbar {
            display: none;
        }
    }
</style>

<style lang="sass" scoped>
.board-table-cards
    padding-right: 24px
    .table-header
        vertical-align: bottom
        text-align: left
        padding: 12px 8px !important
        border: 1px solid #FFFFFF !important

    .table-column
        padding: 4px 8px !important
        border-top: 1px solid #FFFFFF !important
        border-bottom: 1px solid #FFFFFF !important
        background: rgba(71, 70, 106, 0.03)
        color: #47466A
        max-width: 250px
        text-overflow: ellipsis
        overflow : hidden
        white-space: nowrap

        &:hover
            text-overflow: clip
            white-space: normal
            word-break: break-all


    .table-row
        border: 0px solid #FFFFFF !important
        position: relative

        &:hover
            background-color: #edecf0 !important
            
    .form-field-text
        padding-top: 8px !important
        margin-top: 0px !important
        margin-left: 20px

    .download-csv
        justify-content: end
        font-size: 12px
        margin-top: 16px
        align-items: center
        color: var(--v-themeColor-base)
        display: flex

    .table-name
        justify-content: end
        font-size: 18px
        margin-top: 16px
        align-items: center
        color: var(--v-themeColor-base)
        font-weight: bold
        display: flex

.showButtons
    box-sizing: border-box
    width: fit-content
    background: #FFFFFF
    border: 1px solid #D0D5DD
    font-weight: 500
    box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05)


.table-sub-header
    position: relative

.filter-icon
    color: #6200EA !important
    opacity:0.8
    min-width: 0px !important
    position: absolute
    right: -35px
    top: -5px

.list-header
    border: 1px solid #47466A    
    font-weight: 500
    padding: 4px 6px
    color: #47466A
    background: white
    opacity: 1
    font-size: 14px

</style>

<style scoped>

.form-field-text >>> .v-label {
  font-size: 12px;
  color: #6200EA;
  font-weight: 400;
}

.headerContainer{
    display: flex;
    flex-direction: row;
    justify-content: space-between;
}

.headerDiv{
    display: flex;
    flex-wrap: wrap;
}

.headerDiv:first-child{
    /* flex-direction: row-reverse; */
    max-width: 680px;
}

.headerDiv:last-child{
    align-items: center;
    justify-content: flex-end;
}

.v-btn-toggle:not(.v-btn-toggle--dense) .v-btn.v-btn.v-size--default {
    height: 36px; 
}
.v-btn-toggle .v-btn.v-btn.v-size--default {
    min-width: 0px;
    min-height: 0;
}

.filterHeaderSpan{
    min-width: 67px;
    height: 16px;
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 500;
    font-size: 12px;
}

.showEnd{
    order: 99;
}
.form-field-text >>> input {
  font-size: 14px;
  color: #6200EA;
  font-weight: 500;
}

.v-data-table >>> .table-entry {
    font-size: 12px !important;
}
.v-data-table >>> .table-sub-header {
    font-size: 14px !important;
}

.board-table-cards >>> .v-data-footer__select {
    display: none;
}

.board-table-cards >>> .v-data-footer__pagination{
    display: none;
}

</style>