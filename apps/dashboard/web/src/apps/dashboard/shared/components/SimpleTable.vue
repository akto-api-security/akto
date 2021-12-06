<template>
    
    <v-data-table
        :headers="headers"
        :items="items"
        class="board-table-cards keep-scrolling"
        :search="search"
        :sort-by="sortKey"
        :items-per-page="-1"
        hide-default-footer
        hide-default-header>

        <template v-slot:header="{}" v-if="items && items.length > 0">
            <th
                    v-for="(header, index) in headers"
                    class='table-header'
                    :key="index"
                    :style="index == 0 ? {'padding': '2px !important'} : {}"
            >
                <div class="table-sub-header clickable" @click="sortKey = header.value" v-if="index > 0">
                    {{header.text}} 
                    <v-icon :class="sortKey == header.value ? 'black-color-entry' : 'grey-color-entry'">$fas_long-arrow-alt-up</v-icon>
                </div>
            </th>
        </template>
        <template v-slot:footer="{}" v-if="items && items.length > 0">
            <div class="clickable download-csv ma-1">
                <v-icon :color="$vuetify.theme.themes.dark.themeColor">$fas_file-csv</v-icon>
                <span class="ml-2" @click="downloadData">Download as CSV</span>
            </div>
        </template>

        <template v-slot:item="{item}">
            <tr class="table-row">
                <td
                    class="table-column"
                    :style="{'background-color':item.color, 'padding' : '0px !important'}"
                />
                <td 
                    v-for="(entry, index) in headers.slice(1)"
                    :key="index"
                    class="table-column clickable"
                    @click="$emit('rowClicked', item)"
                >
                    <div class="table-entry">{{item[entry.value]}}</div>
                </td>
            </tr>

        </template>
    </v-data-table>
</template>

<script>

import obj from "@/util/obj"
import { saveAs } from 'file-saver'

export default {
    name: "SimpleTable",
    props: {
        headers: obj.arrR,
        items: obj.arrR,
        name: obj.strN
    },
    data () {
        return {
            search: null,
            sortKey: null
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