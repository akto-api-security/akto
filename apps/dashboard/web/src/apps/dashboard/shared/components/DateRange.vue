<template>
    <v-menu
        v-model="showMenu"
        :close-on-content-click="false"
        transition="v-expand-transition"
    >
        <template v-slot:activator="{ on, attrs }" v-bind:dates="currDates" v-bind:formatTitleDate="formatTitleDate">
            <v-btn
                color="var(--themeColor)"
                dark
                v-bind="attrs"
                v-on="on"
                tile
                text
            >
                {{formatTitleDate(currDates)}}
            </v-btn>
        </template>
        <v-list>
            <v-list-item>
                <v-date-picker
                    v-model="dates"
                    range
                    color="var(--themeColor)"
                    :allowed-dates="isDateAllowed"
                    :titleDateFormat="formatTitleDate"
                    @input="datesSelected"
                    class="date-range__container"
                    no-title
                    next-icon="$fas_angle-right"
                    prev-icon="$fas_angle-left"
                />
            </v-list-item>
        </v-list>
    </v-menu>

</template>

<script>
    import obj from "@/util/obj"
    import func from "@/util/func"

    export default {
        name: "DateRange",
        props: {
            datesInit: obj.arrR,
            allowFutureDates: {
              type: Boolean,
              default: false
            }
        },
        model: {
            prop: 'datesInit',
            event: 'input'
        },
        data () {
            return {
                dates: [...this.datesInit],
                currDates: [...this.datesInit],
                showMenu: false
            }
        },
        methods: {
            toYMDFromCalendar(val) {
                return +(val.replace(/\-/g, ''))
            },
            isDateAllowed (val) {
                return this.allowFutureDates || this.toYMDFromCalendar(val) <= func.toYMD(new Date())
            },
            formatTitleDate (mode) {
                var model = mode || this.currDates
                if (!model || model.length == 0){
                    return 'Select dates'
                } else {
                    model = [...model]

                    var ymd1 = this.toYMDFromCalendar(model[0])
                    var d1 = func.toDateStr(new Date(func.toDate(ymd1)));
                    if (model.length == 1) {
                        return d1 + ' - '
                    } else {
                        var ymd2 = this.toYMDFromCalendar(model[1])
                        var d2 = func.toDateStr(new Date(func.toDate(ymd2)));
                        return (ymd1 < ymd2) ? (d1 + ' - ' + d2) : (d2 + ' - ' + d1)
                    }
                }
            },
            datesSelected() {
                if (this.dates && this.dates.length == 2) {
                    this.currDates = this.dates
                    this.$emit('input', this.dates)
                    this.showMenu = false
                }
            },
            diffDays (d1, d2) {
                return +(Math.round(func.toDate(this.toYMDFromCalendar(d2)) - func.toDate(this.toYMDFromCalendar(d1)) )/86400000) + 1
            },
            formatYear () {
                var dates = this.currDates
                if (!dates || dates.length == 0) {
                    return 'No dates selected'
                } else if (dates && dates.length == 1) {
                    return 'Pick end date'
                } else {
                    return 'Selected range'
                }
            },
            getDateStr() {
                return this.formatTitleDate(this.currDates)
            },
            onDateRangeClick () {
                this.dates = [...this.currDates]
                return true
            }
        },
        mounted () {

        },
        watch: {
            'showMenu' : function (newVal, oldVal) {
                if (newVal && !oldVal) {
                    this.dates = [...this.currDates]
                }
            }
        }
    }
</script>

<style lang="sass">
</style>