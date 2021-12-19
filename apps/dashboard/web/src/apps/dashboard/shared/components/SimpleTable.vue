<template>
    <div>
        <v-data-table
        :headers="headers"
        :items="items"
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
            <th
                    v-for="(header, index) in headers"
                    class='table-header'
                    :key="index"
                    :style="index == 0 ? {'padding': '2px !important'} : {}"
            >
                <div class="table-sub-header clickable" @click="setSortOrInvertOrder(header)" v-if="index > 0">
                    {{header.text}} 
                </div>
            </th>
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

export default {
    name: "SimpleTable",
    components: {
        ActionsTray
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
            sortDesc: this.sortDescDefault || false
        }
    },
    methods: {
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
        z-index: 1
        border: 1px solid #FFFFFF !important

    .table-column
        padding: 8px 16px !important
        z-index: 1
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