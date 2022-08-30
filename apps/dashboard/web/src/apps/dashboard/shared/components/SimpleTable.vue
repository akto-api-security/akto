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
            :items-per-page="rowsPerPage"
            :footer-props="{
                showFirstLastPage: true,
                prevIcon: '$fas_angle-left',
                nextIcon: '$fas_angle-right',
                'items-per-page-options': itemsPerPage
                
            }"
            :hide-default-footer="!enablePagination"
            hide-default-header
        >
            <template v-slot:top="{ pagination, options, updateOptions }" v-if="items && items.length > 0">
                <div class="d-flex jc-sb">
                    <div v-if="showName" class="table-name">
                      {{name}}
                    </div>
                    <div class="mt-3">
                        <template v-for="(header, index) in headers">
                            <span :key="'filter_'+index" v-if="header.text && !header.hideFilter">
                                <v-menu :key="index" offset-y :close-on-content-click="false" v-model="showFilterMenu[header.value]"> 
                                    <template v-slot:activator="{ on, attrs }">                         
                                        <v-btn 
                                            :ripple="false" 
                                            v-bind="attrs" 
                                            v-on="on"
                                            primary 
                                            plain
                                            class="px-2"
                                        >
                                            <div class="list-header">
                                                <span>{{header.text}}</span>
                                                <span v-if="filters[header.value].size > 0">({{filters[header.value].size}})</span>
                                                <v-icon>$fas_angle-down</v-icon>
                                            </div>
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
                    <div>
                        <slot name="massActions"/>
                    </div>
                    <div class="d-flex jc-end">
                        <div class="d-flex board-table-cards jc-end">
                            <div class="clickable download-csv ma-1">
                                <v-tooltip bottom>
                                    <template v-slot:activator="{on, attrs}">
                                        <v-btn 
                                            v-on="on"
                                            v-bind="attrs" 
                                            icon color="#47466A" @click="downloadData" v-if="!hideDownloadCSVIcon">
                                                <v-icon>$fas_file-csv</v-icon>
                                        </v-btn>
                                    </template>
                                    Download as CSV
                                </v-tooltip>
                                <v-tooltip bottom>
                                    <template v-slot:activator="{on, attrs}">
                                        <v-btn
                                            v-on="on"
                                            v-bind="attrs"
                                            icon
                                            color="#47466A"
                                            @click="itemsPerPage = [-1]"
                                            v-if="enablePagination && itemsPerPage[0] != -1"
                                        >
                                            <v-icon>$fas_angle-double-down</v-icon>
                                        </v-btn>
                                    </template>
                                    Show all
                                </v-tooltip>
                                        <v-btn
                                            icon
                                            color="#47466A"
                                            @click="itemsPerPage = [rowsPerPage]"
                                            v-if="enablePagination && itemsPerPage[0] == -1"
                                        >
                                            <v-icon>$fas_angle-double-up</v-icon>
                                        </v-btn>
                            </div>
                            <slot name="add-new-row-btn" :filteredItems=filteredItems />
                        </div>
                    </div>
                </div>
            </template>
            <template v-slot:footer.prepend="{}">
                <v-spacer/>
            </template>
            <template v-slot:header="{}" v-if="items && items.length > 0">
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
            <template v-slot:item="{item}">
                <v-hover
                    v-slot="{ hover }"
                >
                    <tr class="table-row" >
                        <td
                            class="table-column"
                            :style="{'background-color':item.color, 'padding' : '0px !important'}"
                        />
                        <td 
                            v-for="(header, index) in headers.slice(1)"
                            :key="index"
                            class="table-column clickable"
                            @click="$emit('rowClicked', item)"
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
import { saveAs } from 'file-saver'
import ActionsTray from './ActionsTray'
import FilterList from './FilterList'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField.vue'

export default {
    name: "SimpleTable",
    components: {
        ActionsTray,
        FilterList,
        SimpleTextField
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
        showName: obj.boolN
    },
    data () {
        let rowsPerPage = 50
        return {
            rowsPerPage: rowsPerPage,
            itemsPerPage: [rowsPerPage],
            search: null,
            sortKey: this.sortKeyDefault || null,
            sortDesc: this.sortDescDefault || false,
            filters: this.headers.reduce((map, e) => {map[e.value] = new Set(); return map}, {}),
            showFilterMenu: this.headers.reduce((map, e) => {map[e.value] = false; return map}, {}),
            filterOperators: this.headers.reduce((map, e) => {map[e.value] = 'OR'; return map}, {})
        }
    },
    methods: {
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
        }
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
            return this.items && this.items.length > this.rowsPerPage
        } 
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

    .table-row-actions
        position: absolute
        right: 30px
        padding: 8px 16px !important

.table-sub-header
    position: relative
    color: #47466A90
    font-weight: 500

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

</style>