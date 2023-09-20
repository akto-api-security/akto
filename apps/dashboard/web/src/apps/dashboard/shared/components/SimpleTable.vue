<template>
    <div>

        <server-table
            :headers="headers"
            :fetchParams="fetchParams"
            :processParams="processParams"
            :getColumnValueList="getColumnValueList"
            :name="name"
            :pageSize="pageSize"
            :actions="actions"
            :sortKeyDefault="sortKeyDefault"
            :sortDescDefault="sortDescDefault"
            :allowNewRow="allowNewRow"
            :hideDownloadCSVIcon="hideDownloadCSVIcon"
            :showName="showName"
            :key="reRenderKey"
            :dense="dense"
            @rowClicked="rowClicked"
            @filterApplied="filterApplied"
        >
            <template v-slot:add-at-top="{filters, filterOperators, sortKey, sortDesc}">
                <div class="d-flex jc-end">
                    <div class="d-flex jc-end">
                        <div class="clickable download-csv">
                            <v-tooltip bottom>
                                <template v-slot:activator="{on, attrs}">
                                    <div v-on="on" v-bind="attrs">
                                        <secondary-button 
                                            color="var(--themeColorDark)" 
                                            text="Export vulnerability report"
                                            @click="$emit('exportAsHTML')" 
                                            v-if="showExportVulnerabilityButton"
                                        />
                                    </div>
                                </template>
                                Export vulnerability report
                            </v-tooltip>
                        </div>
                        <div class="clickable download-csv">
                            <v-tooltip bottom>
                                <template v-slot:activator="{on, attrs}">
                                    <div v-on="on" v-bind="attrs">
                                        <secondary-button 
                                            color="var(--themeColorDark)" 
                                            text="Export"
                                            @click="downloadData(fetchParamsSync(sortKey, sortDesc, 0, 10000, filters, filterOperators))" 
                                            v-if="!hideDownloadCSVIcon"
                                        />
                                    </div>
                                </template>
                                Download as CSV
                            </v-tooltip>
                        </div>
                        <slot name="add-new-row-btn" 
                            v-bind:filteredItems="fetchParamsSync(sortKey, sortDesc, 0, 10000, filters, filterOperators)"
                        />
                    </div>
                </div>
            </template>

            <template v-for="(index, name) in $slots" v-slot:[name]>
                <slot :name="name" />
            </template>
            <template v-for="(index, name) in $scopedSlots" v-slot:[name]="data">
                <slot :name="name" v-bind="data"></slot>
            </template>
        </server-table>

    </div>
</template>

<script>

import obj from "@/util/obj"
import { saveAs } from 'file-saver'
import FilterList from './FilterList'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import ServerTable from './ServerTable'
import SecondaryButton from "./buttons/SecondaryButton.vue"


export default {
    name: "SimpleTable",
    components: {
        FilterList,
        SimpleTextField,
        ServerTable,
        SecondaryButton
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
        showExportVulnerabilityButton: obj.boolN,
        showName: obj.boolN,
        showGridView:obj.boolN,
        leftView:obj.boolN,
        defaultRowHeight: obj.numN,
        dense: obj.boolN,
        pageSize: obj.numN
    },
    data () {
        return {
            reRenderKey: 0
        }
    },
    methods: {
        filterApplied(data) {
            this.$emit("filterApplied", data)
        },
        rowClicked(row, $event) {
            this.$emit('rowClicked', row, $event)
        },
        processParams (x) {
            return {...x}
        },
        getColumnValueList(headerValue) {
            let ret = this.columnValueList[headerValue]
            return {type: "STRING", values: ret}
        },
        fetchParamsSync(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            
            let ret = this.sortFunc(this.items.filter((d) => {
                return Object.keys(filters).every((f) => {
                    return this.filterFunc(d, f, filters, filterOperators)
                });
            }), sortKey, [sortOrder == 1])

            if (limit != -1) {
                ret = ret.slice(skip, skip+limit)
            }
            return ret
        },
        async fetchParams(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            let ret = this.fetchParamsSync(sortKey, sortOrder, skip, -1, filters, filterOperators)
            return {endpoints: ret.slice(skip, skip+limit), total: ret.length}
        },
        valToString(val) {
            if (val instanceof Set) {
                return [...val].join(" & ")
            } else {
                return val || "-"
            }
        },
        downloadData(rows) {
            let headerTextToValueMap = Object.fromEntries(this.headers.map(x => [x.text, x.value]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",")+"\r\n"
            rows.forEach(i => {
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
                return items
            }

            let sortKey = this.headers.find(x => x.value === columnToSort)
            if (sortKey) {
                columnToSort = sortKey.value
            }


            let ret = items.sort((a, b) => {
                if (a[columnToSort] === b[columnToSort]) {                    
                    return 0
                }

                let ret = a[columnToSort] > b[columnToSort] ? -1 : 1
                if (isDesc[0]) {
                    ret = -ret
                }
                return ret
            })

            return ret
        },
        filterFunc(item, header, filters, filterOperators) {
            let itemValue = item[header]
            let selectedValues = filters[header]
            if (filters[header].size < 1) {
                return true
            } 
            
            if (itemValue instanceof Array) {
                itemValue = new Set(itemValue);
            }

            if(itemValue instanceof Set) {
                switch(filterOperators[header]) {
                    case "OR":
                        return [...selectedValues].filter( v => itemValue.has(v)).length > 0 
                    case "AND":
                        return [...selectedValues].filter( v => !itemValue.has(v)).length == 0 
                    case "NOT":
                        return [...selectedValues].filter( v => itemValue.has(v)).length == 0 

                }
            } else {
                let filterValue = (itemValue.value || itemValue.value === 0) ? itemValue.value : itemValue
                switch (filterOperators[header]) {
                    case "OR": 
                    case "AND":
                        return selectedValues.has(filterValue)
                    case "NOT":
                        return !selectedValues.has(filterValue)
                }
            }
        },
        getDistinctItems(array) {
            const seen = new Set();
            return array.filter(item => {
                const key = typeof item === 'object' ? JSON.stringify(item) : item;
                if (!seen.has(key)) {
                    seen.add(key);
                    return true;
                }
                return false;
            });
        }
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

                    let distinctItems = this.getDistinctItems(allItemValues)
                    m[h.value] = distinctItems.map(x => {return {title: x?.title ? x?.title : x, subtitle: '', value: (x?.value || x?.value === 0) ? x?.value : x}})
                    return m
                }, {})
            }
        }
    },
    watch: {
        items: function() {
            this.reRenderKey += 1
        }
    }
}
</script>

<style scoped lang="sass">
.download-csv
    margin-left: 8px !important
</style>