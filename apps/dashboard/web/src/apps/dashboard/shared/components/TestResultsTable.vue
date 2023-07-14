<template>
    <div>
        <div v-if="title">
            <v-btn plain @click="showTable = !showTable">
                <span class="table-heading" :style="{color: color}">{{title}}</span>
                <v-icon :color="color" :style="{transform: showTable ? 'rotate(90deg)' : '', transition: 'all 0.2s linear'}">$fas_angle-right</v-icon>
            </v-btn>
        </div>

        <v-scroll-y-transition>
            <div v-show="showTable">
                <v-data-table
                    :headers="headers"
                    :items="rows"
                    class="board-table-cards keep-scrolling"
                    :search="search"
                    :sort-by="sortKey"
                    :items-per-page="-1"
                    hide-default-footer
                    hide-default-header>
                    <template v-slot:top>
                        <v-text-field
                            v-model="search"
                            label="Search"
                            class="form-field-text"
                            prepend-inner-icon="$fas_search"
                        ></v-text-field>
                    </template>

                    <template v-slot:header="{props}">
                        <th
                                v-for="(header, index) in props.headers"
                                :class="index < 3 ? 'table-header-fixed table-header' : 'table-header'"
                                :key="index"
                        >
                            <div v-if="header.parentHeader" class="table-parent-header">
                                <span>{{header.parentHeader}}</span>
                            </div>
                            <div class="table-sub-header clickable" @click="sortKey = header.value" v-if="index > 0">
                                {{header.text}} 
                                <v-icon :class="sortKey == header.value ? 'black-color-entry' : 'grey-color-entry'">$fas_long-arrow-alt-up</v-icon>
                            </div>
                        </th>
                    </template>

                    <template v-slot:item="{item}">
                        <tr class="table-row">
                            <td
                                class="table-column-fixed table-column"
                                :style="{'background-color': getItemColor(item)}"
                            />
                            <td class="table-column-fixed table-column">
                                <v-tooltip bottom @rowClicked="rowClicked(item)">
                                    <template v-slot:activator='{ on, attrs }'>
                                        <div 
                                            class="goal-name"          
                                            v-bind="attrs"
                                            v-on:click="$emit('rowClicked', item)"
                                            v-on="on">
                                            <div class="goal-name">{{item.uri}}</div>
                                        </div>
                                    </template>
                                    {{item.uri}}
                                </v-tooltip>
                            </td>
                            <td class="table-column-fixed table-column">
                                <div class="goal-period">
                                    {{item.method}}
                                </div>
                            </td>
                            <td class="table-column">
                                <div v-if="item.isHappy">Happy</div>
                                <div v-else>Incorrect input</div>
                            </td>
                            <td class="table-column">
                                <div v-if="isFailed(item)">Failed</div>
                                <div v-else>Passed</div>
                            </td>
                            <td class="table-column">
                                <div>{{item.attemptResult.timeTakenInMillis}}</div>
                            </td>
                        </tr>

                    </template>
                </v-data-table>
            </div>
        </v-scroll-y-transition>
    </div>
</template>

<script>
    import obj from "@/util/obj"
    import func from "@/util/func"

    export default {
        name: "TestResultsTable",
        props: {
            title: obj.strR,
            rows: obj.arrR,
            color: obj.strR,
            width: {
                type: String,
                default: "210px"
            }
        },
        data () {
            return {
                showTable: true,
                search: null,
                sortKey: null
            }
        },
        methods: {
            getItemColor (x) {
                return this.isFailed(x) ? this.$vuetify.theme.themes.dark.redMetric : this.$vuetify.theme.themes.dark.greenMetric
            },
            isFailed(x) {
                return (x.isHappy && x.attemptResult.errorCode > 299) || (!x.isHappy && x.attemptResult.errorCode < 300) && x.attemptResult.errorCode > 0
            },
            rowClicked (x, y) {
                this.$parent.$emit('rowClicked', item)
            }
        },
        computed: {
            headers () {
                return [
                    {
                        text: '',
                        value: 'color'
                    },
                    {
                        text: 'Path',
                        value: 'uri'
                    },
                    {
                        text: 'Method',
                        value: 'method'
                    },
                    {
                        text: 'Type',
                        value: 'type'
                    },
                    {
                        text: 'Test result',
                        value: 'result'
                    },
                    {
                        text: 'Time taken (ms)',
                        value: 'timeTakenInMillis'
                    }
                ]
            }
        }
    }
</script>

<style lang="sass" scoped>
    .goal-name
        font-size: 14px
        color: var(--themeColor)
        white-space: nowrap
        overflow: hidden
        text-overflow: ellipsis
        cursor: pointer
        &:hover
            text-underline: var(--themeColorDark)

    .goal-period
        font-weight: 500
        font-size: 12px
        color: var(--themeColorDark)
        opacity: 0.5

    .goal-action
        font-size: 18px

    .goal-status
        font-weight: 600
        font-size: 14px
        color: var(--themeColorDark)

    .goal-achieved
        font-weight: 500
        font-size: 12px
        color: var(--themeColorDark)
        opacity: 0.5
        padding-top: 8px

    .table-heading
        font-weight: 400
        font-size: 24px
.form-field-text
    padding-top: 8px !important
    margin-top: 0px !important
    margin-left: 20px
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

</style>

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

<style lang="sass">
    .board-table-cards

        .caret-container
            height: 16px
            width: 16px
            border-radius: 2px
            ::before
                color: var(--white)

        .table-parent-header
            font-weight: 700
            font-size: 13px
            color: var(--themeColorDark4)
            padding-bottom: 6px

        .table-sub-header
            font-weight: 500
            font-size: 13px
            color: var(--themeColorDark12)

        .grey-color-entry
            color: var(--themeColorDark11)

        .black-color-entry
            color: var(--themeColorDark2)

        .table-header
            vertical-align: bottom
            text-align: left
            padding: 12px 8px !important
            z-index: 1
            border: 1px solid var(--white) !important

        .table-column
            padding: 8px 16px !important
            z-index: 1
            border: 1px solid var(--white) !important
            background: var(--themeColorDark18)
            color: var(--themeColorDark)
            &:nth-child(1)
                left: 0px
                min-width: 8px !important
                max-width: 8px !important
                padding: 12px 4px !important

            &:nth-child(2)
                left: 8px
                max-width: 210px !important

            &:nth-child(3)
                left: 218px
                max-width: 124px !important

            &:nth-child(4)
                left: 342px
                max-width: 210px !important


        .table-header-fixed
            position: sticky
            top: auto
            z-index: 10 !important
            opacity: 1
            background-color: var(--white)
            &:nth-child(2)
                left: 0px
                min-width: 8px !important
                padding: 12px 4px !important

            &:nth-child(3)
                left: 8px
                min-width: 180px !important

            &:nth-child(4)
                left: 188px
                min-width: 124px !important

            &:nth-child(5)
                left: 342px
                min-width: 136px !important

        .table-column-fixed
            position: sticky
            top: auto
            z-index: 10 !important
            opacity: 1
            background-color: var(--colTableBackground)
            &:nth-child(1)
                left: 0px
                min-width: 8px !important
                max-width: 8px !important
                padding: 12px 4px !important

            &:nth-child(2)
                left: 8px
                min-width: 180px !important

            &:nth-child(3)
                left: 188px
                min-width: 124px !important

            &:nth-child(4)
                left: 342px
                min-width: 136px !important

        .table-row
            border: 0px solid var(--white) !important

            &:hover
                background-color: var(--colTableBackground) !important

</style>