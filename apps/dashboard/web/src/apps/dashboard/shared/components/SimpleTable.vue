<template>
    <div class="tableContainer" @click="selectTable">        
        <v-data-table
            :headers="headers"
            :items="filteredItems"
            class="board-table-cards keep-scrolling"
            :class="{horizontalView:showGridView && !leftView}"
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
                    <div v-if="showGridView && !leftView" class="gridName">
                        <h1>{{ name }}</h1>
                    </div>
                    <div class="headerDiv" v-if="!leftView">
                        <div v-if="showName && !showGridView" class="table-name">
                            {{name}}
                        </div>                 
                        <div>
                            <slot name="massActions"/>
                        </div>

                        <span class="headerButtonContainer showEnd">
                            <v-menu offset-y :close-on-content-click="false" v-if="headers.length > 4">
                                <template v-slot:activator="{ on, attrs }">
                                    <v-btn class = "showButtons noBorderButton" v-bind= "attrs" v-on = "on">
                                        <span class="filterHeaderSpan" :style="{'color':'#676767'}">
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
                        </span>

                        <template v-for = "(header,index) in selectedHeaders">
                            <span class="headerButtonContainer" :key="index">
                                <v-menu :key="index" offset-y :close-on-content-click="false" v-model="showFilterMenu[header.value]"> 
                                    <template v-slot:activator="{ on, attrs }">
                                        <v-btn class = "showButtons" v-bind = "attrs" v-on = "on">
                                            <span class="filterHeaderSpan" :style="filters[header.value].size > 0 ? {'color': '#6200EA !important'} : {}">
                                                {{header.text}}
                                                <v-icon :size="16">$fas_angle-down</v-icon>
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

                    <!-- <div v-if="filteredItems.length < items.length">
                        <span class="headerButtonContainer">
                            <v-btn class = "showButtons noBorderButton" plain @click="clearFilters()">
                                <span class="filterHeaderSpan">
                                    Clear All Filters
                                    <v-icon :size="16">$fas_times</v-icon>
                                </span>
                            </v-btn>
                        </span>
                    </div> -->

                    <div class="headerDiv" v-if="!showGridView">
                        <!-- <v-btn-toggle
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
                                            <v-icon :size="16">{{item.icon}}</v-icon>
                                        </v-btn>
                                    </template>
                                    {{item.toolTipText}}
                                </v-tooltip>
                            </template>
                        </v-btn-toggle> -->
                        <v-data-footer 
                            v-if="itemsPerPage > 0"
                            :pagination="pagination" 
                            :options="options"
                            @update:options="updateOptions"
                            prevIcon= '$fas_angle-left'
                            nextIcon= '$fas_angle-right'
                        />
                        <div class="clickable download-csv">
                            <v-tooltip bottom>
                                <template v-slot:activator="{on, attrs}">
                                    <v-btn 
                                        v-on="on"
                                        v-bind="attrs" 
                                        class="showButtons"
                                        @click="downloadData" v-if="!hideDownloadCSVIcon && !slotActions"
                                    >
                                        <span class="filterHeaderSpan adjust-width">
                                            Export
                                            <v-icon :size="16">$fas_upload</v-icon>
                                        </span>
                                    </v-btn>
                                </template>
                                Download as CSV
                            </v-tooltip>
                            <div>
                                <slot name="add-new-row-btn" :filteredItems=filteredItems />
                            </div>
                        </div>
                        <span class="dropdownActions" v-if="!hideMoreActions">
                            <v-menu offset-y>
                                <template v-slot:activator="{ on, attrs }">
                                    <v-btn
                                    dark
                                    text
                                    v-bind="attrs"
                                    v-on="on"
                                    >
                                        <v-tooltip top>
                                            <template v-slot:activator="{ on, attrs }">
                                                <v-icon color="#47466A" v-on="on" v-bind="attrs">$dropdown</v-icon>
                                            </template>
                                            More Actions
                                        </v-tooltip>
                                    </v-btn>
                                </template>
                                <v-list>
                                    <v-tooltip bottom>
                                        <template v-slot:activator="{on, attrs}">
                                            <div class="exportList" 
                                                v-if="slotActions" 
                                                @click="downloadData"
                                                v-on="on"
                                                v-bind="attrs"
                                            >
                                                Export
                                            </div>
                                        </template>
                                        Download as CSV
                                    </v-tooltip>
                                    
                                    <v-list-item-title>Show Up to</v-list-item-title>
                                    <v-list-item
                                        v-for="(val, index) in itemsPerPageArray"
                                        :key="index"
                                        @click="updateItemsPerPage(val)"
                                    >
                                        <v-list-item-title v-if="val > 0">{{ val }}</v-list-item-title>
                                        <v-list-item-title v-else>Show All</v-list-item-title>
                                        <v-list-item-subtitle v-if="val === itemsPerPage">
                                            <v-icon :size="16">$fas_check</v-icon>
                                        </v-list-item-subtitle>
                                    </v-list-item>
                                </v-list>
                            </v-menu>
                        </span>
                    </div>
                </div>
            </template>
            <template v-slot:footer.prepend="{}">
                <v-spacer/>
            </template>

            <template v-slot:header="{}" v-if="!showGridView">
                <template v-for="(header, index) in headers">
                    <th
                            class='table-header'
                            :style="index == 0 ? {'padding': '0px !important'} : {}" 
                            :key="index"
                    >
                        <!-- <div v-if="index == 1 && actions">
                            <span class="table-sub-header" />
                        </div> -->
                        <div v-if="index > 0">
                                <span class="table-sub-header" :style="index === 1 ? {'margin-left':'0px !important'} : {}">
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

            <template v-slot:header="{}" v-else-if="!leftView && showGridView">
                <div class="showAllButton">
                    <span class="buttonText" v-if="!activeButton" @click="showAll()">    
                        Show All
                        <v-icon>$fas_angle-down</v-icon>
                    </span>
                    <span class="buttonText" v-else @click="showAll()">    
                        Hide All
                        <v-icon>$fas_angle-up</v-icon>
                    </span>              
                </div>
            </template>
            
            <template v-slot:item="{item}">
                <slot name="gridView" :rowData="item" :length="filteredItems.length">
                    <tr class="table-row" 
                    @click="rowSelected(item)"
                    :style="currentRow === item ? {'background':'rgba(71, 70, 106, 0.7)'} : {}" 
                    >
                        <td
                            class="table-column high-dense"
                            :style="{'background-color':item.color, 'padding' : '0px !important'}"
                        />
                        <!-- <td v-if="actions && actions.length > 0" class="table-column high-dense">
                            <input type="checkbox" id="checkbox" v-model="batchSelection" :value="item" />
                        </td> -->
                        <td
                            v-for="(header, index) in headers.slice(1)"
                            :key="index"
                            class="table-column clickable table-entry"
                            @click="$emit('rowClicked', item)"
                            :style="adjustHeight"
                        >
                            <slot :name="[`item.${header.value}`]" :item="item" >
                                <div class="table-entry" :style="index === 0 ? {'margin-left':'0px !important'} : {}">
                                    <span v-if="item[header.value]" 
                                        :style="currentRow === item ? {'color':'#FFFFFF'}: {}" 
                                        class="changeColor"
                                    > 
                                        {{ item[header.value] }}
                                    </span>
                                    <span class="centerDiv changeColor" :style="currentRow === item ? {'color':'#FFFFFF'}: {}" v-else>-</span>
                                </div>
                            </slot>
                        </td>

                        <div v-if="actions && actions.length > 0" class="table-row-actions" :style="adjustHeight">
                            <simple-menu :items="actionsFunction(item)">
                                <template v-slot:activator2>
                                    <v-icon :style="currentRow === item ? {'color':'#FFFFFF'}: {}">
                                        $dropdown
                                    </v-icon>
                                </template>
                            </simple-menu>
                        </div>
                    </tr>
                </slot>
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
        showGridView:obj.boolN,
        leftView:obj.boolN,
        hideMoreActions:obj.boolN,
        slotActions:obj.boolN,
    },
    data () {
        return {
            batchSelection:[],
            pageNum:1,
            activeButton:false,
            initialDisplayedItems:[],
            selectedHeaders: [],
            currentRow: {},
            itemsPerPage: 10,
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
        showAll(){
            this.activeButton = !this.activeButton
            if(this.activeButton){
                this.itemsPerPage = -1
            }else{
                this.itemsPerPage = 5
            }

        },
        clearFilters(){
            this.filters= this.headers.reduce((map, e) => {map[e.value] = new Set(); return map}, {})
            this.showFilterMenu= this.headers.reduce((map, e) => {map[e.value] = false; return map}, {})
            this.filterOperators= this.headers.reduce((map, e) => {map[e.value] = 'OR'; return map}, {})
        },
        selectTable(){
            this.$store.state.globalUid = this._uid
        },
        storeCurrentItems(items){
            this.initialDisplayedItems = items
        },
        selectRow(index){
            this.rowSelected(this.initialDisplayedItems[index])
            this.$emit('rowClicked',this.initialDisplayedItems[index])
        },
        moveRowsOnKeys(event){
            if(this.$store.state.globalUid !== this._uid){
                // console.log("return")
                return 
            }

            if(event.keyCode < 37 || event.keyCode > 40){
                // console.log('return')
                return
            }
            
            switch(event.keyCode){
                case 37:
                    this.moveLeft()
                    break

                case 38:
                    this.moveUp()
                    break

                case 39:
                    this.moveRight()
                    break

                case 40:
                    this.moveDown()
                    break
            }
        },
        rowSelected(item){
            this.currentRow = {}
            this.currentRow = item
        },
        moveDown(){
            if(this.currentRow){
                var index = this.initialDisplayedItems.indexOf(this.currentRow)
                var n = this.initialDisplayedItems.length - 1
                if(index === n){
                    this.pageNum++
                    this.currentRow = {}
                }

                else{
                    index++
                    this.selectRow(index)
                }
            }
            else{
                this.selectRow(0)
            }
        },
        moveUp(){
            if(this.currentRow){
                var index = this.initialDisplayedItems.indexOf(this.currentRow)
                if(index === 0){
                    if(this.pageNum > 1){
                        this.pageNum--
                        this.currentRow = {}
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
        },
        moveLeft(){
            if(this.pageNum > 1){
                this.pageNum--
            }
        },
        moveRight(){
            var totalPages = Math.ceil((this.filteredItems.length / this.itemsPerPage))
            if(this.pageNum < totalPages){
                this.pageNum++
            }
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
    destroyed() {
        window.removeEventListener('keydown',this.moveRowsOnKeys,null)
        this.$store.state.globalUid = -1
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
            if(this.leftView){
                this.itemsPerPage = -1
            }

            else if(this.showGridView){
                this.itemsPerPage = 5
            }
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
        adjustHeight(){
            var height = this.toggle_exclusive
            return{
                height: height + 'px'
            }
        },
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

    .showAllButton{
        cursor: pointer;
        display: flex;
        justify-content: end;

        .buttonText{
            font-family: 'Poppins';
            font-style: normal;
            font-weight: 500;
            font-size: 16px;
            line-height: 130%;
            color: #C9C9C9;
        }
    }
    
    .horizontalView{
        tbody{
            display: flex;
            gap: 18px;
            flex-direction: row;
            margin: 12px 5px 0px 5px;
            flex-wrap: wrap;
        }
    }

    .table-row{
        &:hover{
            .changeColor{
                color: #47466A !important;
            }

            .table-row-actions{
                opacity: 1 !important;
            }
        }
    }

    .centerDiv{
        margin-left: 8px;
    }

    .v-chip-group .v-slide-group__content {
        padding: 0px !important;
        margin-left: 24px !important;
    }

</style>

<style lang="sass" scoped>
.board-table-cards
    padding-right: 24px
    cursor: pointer
    align-items: center !important
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

        .table-row-actions
            opacity: 0
            position: absolute
            right: 30px
            padding: 12px 16px !important
    .form-field-text
        padding-top: 8px !important
        margin-top: 0px !important
        margin-left: 20px

    .download-csv
        justify-content: end
        font-size: 12px
        color: var(--v-themeColor-base)
        display: flex
        margin-top: 5px
        gap: 8px

    .table-name
        justify-content: end
        font-size: 18px
        margin-top: 16px
        align-items: center
        color: var(--v-themeColor-base)
        font-weight: bold
        display: flex

.showButtons
    box-sizing: border-box !important
    width: fit-content !important
    background: #fff !important
    border: 1px solid #d0d5dd
    font-weight: 500
    height: 30px !important
    display: flex !important
    flex-direction: row !important
    padding: 4px 8px !important
    gap: 4px !important
    align-items: center !important
    box-shadow: 0px 1px 2px rgb(16 24 40 / 5%)
.table-sub-header
    position: relative
</style>

<style scoped>


.v-tooltip__content:after{
    border:#7e7e97 !important;
}
.form-field-text >>> .v-label {
  font-size: 12px;
  color: #6200EA;
  font-weight: 400;
}

.headerButtonContainer{
    display: flex;
    margin: 5px 8px 0px 0px ;
}
.selectRowsPerPage{
    color:black;
    margin-right: 5px;
}

.gridName {
    align-items: center;
    padding: 5px 0px 10px 20px;
}
.tableContainer{
    margin-bottom: 15px !important;
    margin-top: 15px !important;
}

.v-data-table >>> .v-data-table__wrapper {
    margin-top:10px;
    overflow-x: auto;
    overflow-y: hidden;
    background: #FFFFFF;
}

.noBorderButton{
    box-shadow: none;
    border: none !important;
}

.adjust-width{
    width: 70px !important;
}


.headerDiv{
    display: flex;
    flex-wrap: wrap;
}

.exportList{
    margin: 8px 0px 30px 0px;
    padding: 4px 8px;
    border-bottom: 1px solid;
    cursor: pointer;
}

.headerDiv:first-child{
    width: 630px;
}

.headerDiv:last-child{
    justify-content: flex-end;
    gap: 8px;
}
.v-btn-toggle .v-btn.v-btn.v-size--default {
    min-width: 0px;
    min-height: 0;
}

.v-btn:not(.v-btn--round).v-size--default {
    height: 30px;
    min-width: 40px;
    padding: 0 8px;
}
.theme--light.v-btn-toggle:not(.v-btn-toggle--group) .v-btn.v-btn .v-icon {
    color: #47466A;
}
.filterHeaderSpan{
    height: 16px;
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 500;
    font-size: 12px;
    justify-content: flex-start;
    align-items: center;
    display: flex;
    gap:3px;
    color:#47466A !important;
}

.v-list-item__title {
    align-self: center;
    font-size: 1rem;
    min-width: 100px;
    padding-left: 8px;
}

.dropdownActions{
    margin-top: 5px;
    width:20px;
    display: flex;
    justify-content: center;
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
    margin-left: 24px;
}

.v-data-table >>> .table-sub-header {
    font-size: 14px !important;
    margin-left: 24px;
    display: flex;
}

.board-table-cards >>> .v-data-footer__select {
    display: none !important;
}

.board-table-cards >>> .v-data-footer__pagination {
    height: 30px;
    margin: 0 5px 0 5px;
    line-height: 2.5;
    font-size: 0.8rem;
    order: 1;
    color:#47466A;
}
.board-table-cards >>> .v-data-footer{
    border: none;  
    height: 30px;
    padding:0px !important;
    background: #FFFFFF;
    border-radius: 4px;
    margin-top:5px;
    border: 1px solid #D0D5DD;
}
.board-table-cards >>> .v-data-footer__icons-before {
    width: 30px;
    display: flex;
    justify-content: flex-start;
    height: 30px !important;
    align-items: center;
}
.board-table-cards >>> .v-data-footer__icons-after {
    order: 2;
    width: 30px;
    display: flex;
    justify-content: flex-end;
    height: 30px !important;
    align-items: center;
}

.v-btn-toggle .v-btn.v-btn.v-size--default[data-v-1f3b7669] {
    height: 30px;
}


.board-table-cards >>> .headerContainer{
    display: flex ;
    flex-direction: row ;
    justify-content: space-between;
}

</style>