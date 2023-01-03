<template>
    <div class="show-schedule-box">
        <div class="d-flex" style="gap: 20px">
            <div>Select time: </div>
            <div>
                <simple-menu :items="hourlyTimes">
                    <template v-slot:activator2>
                        <div class="clickable">{{hourOfDay}}:00</div>
                    </template>

                </simple-menu>
            </div>
            <v-checkbox
                v-model="recurringDaily"
                label="Run daily"
                on-icon="$far_check-square"
                off-icon="$far_square"
                class="run-daily-box"
                :ripple="false"
            />

        </div>

        <v-btn primary dark color="#6200EA" @click="schedule" class="my-2">
            Schedule {{recurringDaily ? "daily" : "today"}} at {{hourOfDay}}:00
        </v-btn>
    </div>
</template>

<script>
import func from '@/util/func'
import SimpleMenu from "@/apps/dashboard/shared/components/SimpleMenu"

export default {
    name: "ScheduleBox",
    components: {
        SimpleMenu
    },
    data() {
        let hours = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
        let amTimes = hours.map(x => {return {label: x + (x==12? " noon": " am"), click: () => this.setHour(x)}})
        let pmTimes = hours.map(x => {return {label: x + (x==12? " midnight": " pm"), click: () => this.setHour(x+12)}})
        
        return {
            hourOfDay: 10,
            recurringDaily: false,
            hourlyTimes: [...amTimes, ...pmTimes]
        }
    },
    methods: {
        setHour(hour) {
            this.hourOfDay = hour
        },
        schedule() {
            let dayStart = +func.dayStart(+new Date());
            let startTimestamp = parseInt(dayStart/1000) + this.hourOfDay * 60 * 60
            return this.$emit("schedule", {recurringDaily: this.recurringDaily, startTimestamp})
        }
    }
}
</script>

<style lang="sass" scoped>
.show-schedule-box
    background: #FFFFFF
    color: #47466A
</style>

<style scoped>
.v-input--selection-controls {
    padding-top: 0px !important;
    margin-top: 0px !important;
}
</style>
