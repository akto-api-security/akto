<template>
    <div>
        <v-data-table
            :headers="headers"
            :items="filteredItems"
            class="board-table-cards keep-scrolling"
            :server-items-length="total"
            :options.sync="options"
            :sort-by="sortKey"
            :sort-desc="sortDesc"
            :items-per-page="rowsPerPage"           
            :hide-default-footer="false"
            :footer-props="{
                showFirstLastPage: false,
                prevIcon: '$fas_angle-left',
                nextIcon: '$fas_angle-right',
                'items-per-page-options': itemsPerPage                
            }"

            hide-default-header
            :loading="loading"
            tabindex="0"
            @keydown.native.37="moveLeft"
            @keydown.native.38="moveUp"
            @keydown.native.39="moveRight"
            @keydown.native.40="moveDown"
            @keydown.native.13="pressEnter"
            :ref="tableId"
        >
            <template v-slot:top="{ pagination, options, updateOptions }">
                <div class="d-flex jc-sb mt-4">
                    
                        <div v-if="showName" class="table-name">
                        {{name}}
                        </div>
                        <div class="d-flex jc-sb">
                            <template v-for = "(header,index) in selectedHeaders">
                                <v-menu :key="index" offset-y :close-on-content-click="false" v-model="showFilterMenu[header.value]"> 
                                    <template v-slot:activator="{ on, attrs }">
                                        <secondary-button 
                                            :text="header.text" 
                                            v-bind="attrs"
                                            v-on="on"
                                            :color="filters[header.sortKey || header.value].size > 0 ? '#6200EA !important' : null"
                                        />
                                    </template>
                                    <filter-column 
                                        :title="header.text"
                                        :typeAndItems="getColumnValueList(header.sortKey || header.value)" 
                                        @clickedItem="appliedFilter(header.sortKey || header.value, $event)" 
                                        @operatorChanged="operatorChanged(header.sortKey || header.value, $event)"
                                        @selectedAll="selectedAll(header.sortKey || header.value, $event)"
                                    />
                                </v-menu>
                            </template>
                            <v-menu offset-y :close-on-content-click="false" v-if="headers.length > 4">
                                <template v-slot:activator="{ on, attrs }">
                                    <secondary-button 
                                        text="More Filters" 
                                        v-bind="attrs"
                                        v-on="on"
                                    />
                                </template>

                                <filter-list
                                    :title="headers[1].text"
                                    hideOperators
                                    hideListTitle
                                    :items="convertHeadersList()"
                                    @clickedItem = "pushIntoNew($event)"
                                />

                            </v-menu>

                        </div>
                        <div>
                            <slot name="massActions"/>
                        </div>
                        <div class="d-flex jc-end">
                            <div class="d-flex board-table-cards jc-end">
                                <slot name="add-new-row-btn" 
                                    v-bind:filters="filters"  
                                    v-bind:filterOperators="filterOperators"
                                    v-bind:sortKey="sortKey"
                                    v-bind:sortDesc="sortDesc"
                                    v-bind:total="total"
                                />
                            </div>
                        </div>
                    
                </div>
            </template>
            <template v-slot:footer.prepend="{}">
                <v-spacer/>
            </template>
            <template v-slot:header="{}">
                <template v-for="(header, index) in headers">
                    <v-hover v-slot="{ hover }" :key="index">
                        <th
                                class='table-header'
                                :style="index == 0 ? {'padding': '2px !important'} : {}"
                        >
                            <div v-if="index > 0">
                                
                                    <span class="table-sub-header">
                                        <span class="clickable"  @click="setSortOrInvertOrder(header)">
                                            {{header.text}} 
                                        </span>
                                    </span>
                                
                            </div>
                        </th>
                    </v-hover>
                </template>
                <template>
                        <tr class="table-row" >
                            <slot name="add-new-row" />
                        </tr>                
                </template>

            </template>
            <template v-slot:item="{item, index}">
                <v-hover
                    v-slot="{ hover }"
                >
                    <tr
                        :class="['table-row', index == currRowIndex ? 'highlight-row' : '']"
                    >
                        <td
                            class="table-column"
                            :style="{'background-color':item.color, 'padding' : '0px !important', 'width': item.width, 'height': dense ? '24px !important' : '48px'}"
                        />
                        <td 
                            v-for="(header, ii) in headers.slice(1)"
                            :key="ii"
                            class="table-column clickable"
                            @click="clickRow(item, index)"
                            :style="{'height': dense ? '24px !important' : '48px'}"
                        >
                            <slot :name="[`item.${header.value}`]" :item="item">
                                <div class="table-entry">{{item[header.value]}}</div>
                            </slot>
                        </td>

                        <div v-if="actions && hover && actions.length > 0" class="table-row-actions">
                            <actions-tray :actions="actions || []" :subject=item></actions-tray>
                        </div>
                    </tr>
                </v-hover>
            </template>
        </v-data-table>
    </div>
</template>

<script>

import obj from "@/util/obj"
import func from "@/util/func"
import { saveAs } from 'file-saver'
import ActionsTray from './ActionsTray'
import FilterColumn from './FilterColumn'
import FilterList from './FilterList'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField.vue'
import SecondaryButton from './buttons/SecondaryButton'

export default {
    name: "ServerTable",
    components: {
        ActionsTray,
        FilterColumn,
        SimpleTextField,
        FilterList,
        SecondaryButton
    },
    props: {
        headers: obj.arrR,
        fetchParams: Function,
        processParams: Function,
        getColumnValueList: Function,
        name: obj.strN,
        sortKeyDefault: obj.strN,
        sortDescDefault: obj.boolN,
        actions: obj.arrN,
        allowNewRow: obj.boolN,
        hideDownloadCSVIcon: obj.boolN,
        showName: obj.boolN,
        dense: obj.boolN
    },
    data () {
        let rowsPerPage = 100
        return {
            tableId: "table_"+parseInt(Math.random() * 10000000),
            options:{},
            rowsPerPage: rowsPerPage,
            itemsPerPage: [rowsPerPage],
            filteredItems: [],
            selectedHeaders: [],
            total: 0,
            currRowIndex: 0,
            loading: false,
            currPage: 1,
            search: null,
            sortKey: this.sortKeyDefault || null,
            sortDesc: this.sortDescDefault || false,
            filters: this.headers.reduce((map, e) => {map[e.sortKey || e.value] = new Set(); return map}, {}),
            showFilterMenu: this.headers.reduce((map, e) => {map[e.sortKey || e.value] = false; return map}, {}),
            filterOperators: this.headers.reduce((map, e) => {map[e.sortKey || e.value] = 'OR'; return map}, {})
        }
    },
    methods: {
        showHideFilterIcon(hValue) {
            return (this.filterOperators[hValue] === "OR" && this.filters[hValue].size == 0)
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
            this.showHideFilterIcon(hValue)
            this.options = {...this.options, page: 1}
        },
        appliedFilter (hValue, {item, checked, operator, type, min, max, searchText}) { 
            if (type === "INTEGER") {
                this.filters[hValue] = [min, max]
            } else if (type === "SEARCH") {
                this.filters[hValue] = [searchText]
            } else {

                this.filterOperators[hValue] = operator || 'OR'
                if (checked) {
                    this.filters[hValue].add(item.value)
                } else {
                    this.filters[hValue].delete(item.value)
                }
            }
            this.filters = {...this.filters}
            this.showHideFilterIcon(hValue)
            this.options = {...this.options, page: 1}
        },
        operatorChanged(hValue, {operator}) {
            this.filterOperators[hValue] = operator || 'OR'
            this.showHideFilterIcon(hValue)
            this.options = {...this.options, page: 1}
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
        pageUpdateFunction(pageNum) {
            this.currPage = pageNum
            this.fetchRecentParams()
        },
        emptyArr(size) {
            let ret = Array(size)
            for(var i = 0; i < size; i ++) {
                ret[i] = this.headers.reduce((z, e) => {
                    z[e.value] = ''
                    return z
                }, {})
            }

            return ret
        },
        fetchRecentParams() {
            this.loading = true
            const { sortBy, sortDesc, page, itemsPerPage } = {...this.options, sortKey: this.sortKey, sortDesc: [this.sortDesc]}
            this.currPage = page
            this.rowsPerPage = itemsPerPage
            let skip = (this.currPage-1)*this.rowsPerPage
            
            this.fetchParams(sortBy[0], sortDesc[0] ? -1: 1, skip, this.rowsPerPage, this.filters, this.filterOperators).then(resp => {
                this.loading = false
                let params = resp.endpoints
                let total = resp.total
                this.total = total
                let listParams = params.map(this.processParams)
                let sortedParams = listParams
                this.filteredItems = sortedParams
            }).catch(e => {
                this.loading = false
            })
        },
        setSortOrInvertOrder (header) {
            let headerSortKey = header.sortKey || header.value
            if (this.sortKey === headerSortKey) {
                this.sortDesc = !this.sortDesc
            } else {
                this.sortKey = headerSortKey
            }
            this.fetchRecentParams()
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
        getDataFromApi () {
            this.loading = true
            this.$refs[this.tableId].$el.focus()
            this.fetchRecentParams()
            this.$refs[this.tableId].$el.focus()
            this.currRowIndex = 0
        },
        convertHeadersList(){
            let skipCount = this.headers[0].value == 'color' ? 5 : 4
            return this.headers.slice(skipCount).map(x => {return {title: x.text, ...x}})
        },
        pushIntoNew({item , checked}){
            if(checked) {
                this.selectedHeaders.push(item)
            }
            else{
                let index = this.selectedHeaders.findIndex(x => x.value === item.value)
                this.selectedHeaders.splice(index, 1)

                this.filters[item.sortKey || item.value] = new Set()
                this.filters = {...this.filters}

                this.operatorChanged(item.sortKey || item.value, {operator: "OR"})
            }
        },
        fillInitial(){
            let skipCount = this.headers[0].value == 'color' ? 1 : 0
            this.selectedHeaders = this.headers.slice(skipCount, skipCount + 4)
        },
        changePage(page) {
            this.options = {...this.options, page}
        },
        moveLeft(){
            let currPage = this.options.page
            if (currPage == 1) return
            
            this.changePage(currPage-1)
        },
        moveUp() {
            if (this.currRowIndex == 0) {
                this.moveLeft()
            } else {
                this.currRowIndex--
            }
        },
        moveRight(){
            let currPage = this.options.page
            if (currPage * this.rowsPerPage >= this.total) return
            
            this.changePage(currPage+1)
        },
        moveDown() {
            if (this.currRowIndex == this.filteredItems.length-1) {
                this.moveRight()
            } else {
                this.currRowIndex++
            }
        },
        pressEnter() {
            let item = this.filteredItems[this.currRowIndex]
            this.$emit('rowClicked', item)
        },
        clickRow(item, index) {
            this.currRowIndex = index
            this.pressEnter()
        }
    },
    watch: {
      options: {
        handler () {
          this.getDataFromApi()
        },
        deep: true,
      },
    },
    mounted () {
        this.fetchRecentParams()
        this.fillInitial()
    }

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
        border: 1px solid var(--white) !important

    .table-column
        padding: 4px 8px !important
        border-top: 1px solid var(--white) !important
        border-bottom: 1px solid var(--white) !important
        background: var(--themeColorDark18)
        color: var(--themeColorDark)
        max-width: 250px
        text-overflow: ellipsis
        overflow : hidden
        white-space: nowrap

        &:hover
            text-overflow: clip
            white-space: normal
            word-break: break-all


    .table-row
        border: 0px solid var(--white) !important
        position: relative

        &:hover
            background-color: var(--colTableBackground) !important
            
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

    .table-row-actions
        position: absolute
        right: 30px
        padding: 8px 16px !important
    
    &:focus    
        outline: none !important

.table-sub-header
    position: relative

.highlight-row
    background-color: var(--themeColorDark14)        

.filter-icon
    color: var(--themeColor) !important
    opacity:0.8
    min-width: 0px !important
    position: absolute
    right: -35px
    top: -5px
</style>

<style scoped>
.form-field-text >>> .v-label {
  font-size: 12px;
  color: var(--themeColor);
  font-weight: 400;
}

.form-field-text >>> input {
  font-size: 14px;
  color: var(--themeColor);
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

</style>