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
        :items-per-page="10"
        :footer-props="{
            showFirstLastPage: true,
            firstIcon: '$fas_angle-double-left',
            lastIcon: '$fas_angle-double-right',
            prevIcon: '$fas_angle-left',
            nextIcon: '$fas_angle-right'
        }"
        :hide-default-footer="!items || items.length == 0"
        hide-default-header>

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
                                    <span>
                                        <v-menu :key="index" offset-y :close-on-content-click="false" v-model="showFilterMenu[header.value]"> 
                                            <template v-slot:activator="{ on, attrs }">                         
                                                <v-btn 
                                                    :ripple="false" 
                                                    v-bind="attrs" 
                                                    v-on="on"
                                                    primary 
                                                    icon
                                                    class="filter-icon" 
                                                    :style="{display: hover || showFilterMenu[header.value] || filters[header.value].size > 0 ? '' : 'none'}"
                                                >
                                                    <v-icon :size="14">$fas_filter</v-icon>
                                                </v-btn>
                                            </template>
                                            <filter-list :title="header.text" :items="columnValueList[header.value]" @clickedItem="appliedFilter(header.value, $event)" />
                                        </v-menu>
                                    </span>
                                </span>
                            
                        </div>
                    </th>
                </v-hover>
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
        <template v-slot:footer.prepend v-if="items && items.length > 0">
            <div class="clickable download-csv ma-1">
                <v-icon :color="$vuetify.theme.themes.dark.themeColor">$fas_file-csv</v-icon>
                <span class="ml-2" @click="downloadData">Download as CSV</span>
            </div>
        </template>

        </v-data-table>
       
        
    </div>
</template>

<script>

import obj from "@/util/obj"
import { saveAs } from 'file-saver'
import ActionsTray from './ActionsTray'
import FilterList from './FilterList'

export default {
    name: "SimpleTable",
    components: {
        ActionsTray,
        FilterList
    },
    props: {
        headers: obj.arrR,
        items: obj.arrR,
        name: obj.strN,
        sortKeyDefault: obj.strN,
        sortDescDefault: obj.boolN,
        actions: obj.arrN
    },
    data () {
        return {
            search: null,
            sortKey: this.sortKeyDefault || null,
            sortDesc: this.sortDescDefault || false,
            filters: this.headers.reduce((map, e) => {map[e.value] = new Set(); return map}, {}),
            showFilterMenu: this.headers.reduce((map, e) => {map[e.value] = false; return map}, {})
        }
    },
    methods: {
        appliedFilter (hValue, {item, checked}) { 
            if (checked) {
                this.filters[hValue].add(item)
            } else {
                this.filters[hValue].delete(item)
            }
            this.filters = {...this.filters}
        },
        downloadData() {
            let headerTextToValueMap = Object.fromEntries(this.headers.map(x => [x.text, x.value]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",")+"\r\n"
            this.items.forEach(i => {
                csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
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
        }
    },
    computed: {
        columnValueList: {
            get () {
                return this.headers.reduce((m, h) => {
                    m[h.value] = [...new Set(this.items.map(i => i[h.value]).sort())]
                    return m
                }, {})
            }
        },
        filteredItems() {
            return this.items.filter((d) => {
                return Object.keys(this.filters).every((f) => {
                return this.filters[f].size < 1 || this.filters[f].has(d[f]);
                });
            });
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
    .table-header
        vertical-align: bottom
        text-align: left
        padding: 12px 8px !important
        border: 1px solid #FFFFFF !important

    .table-column
        padding: 8px 16px !important
        border-top: 1px solid #FFFFFF !important
        border-bottom: 1px solid #FFFFFF !important
        background: rgba(71, 70, 106, 0.03)
        color: #47466A

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

    .table-row-actions    
        position: absolute
        right: 30px
        padding: 8px 16px !important

.table-sub-header
    position: relative

.filter-icon
    color: #6200EA !important
    min-width: 0px !important
    position: absolute
    right: -35px
    top: -5px
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

</style>