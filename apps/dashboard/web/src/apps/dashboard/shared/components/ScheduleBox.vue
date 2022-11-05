<template>
    <div class="show-schedule-box">
        <v-time-picker
            v-model="timePicker"
            ampm-in-title
            color="#6200EA"
            header-color="#6200EA"
            full-width	
        />

        <v-checkbox
            v-model="recurringDaily"
            label="Run daily"
            on-icon="$far_check-square"
            off-icon="$far_square"
            class="ml-2"
            :ripple="false"
        />


        <v-btn primary dark color="#6200EA" @click="schedule" class="ma-2">
            Schedule {{recurringDaily ? "daily" : "today"}} at {{timePicker}}
        </v-btn>
    </div>
</template>

<script>
import func from '@/util/func'

export default {
    name: "ScheduleBox",
    data() {
        return {
            timePicker: "10:00",
            recurringDaily: false
        }
    },
    methods: {
        schedule() {
            let hours = this.timePicker.split(":")[0]
            let minutes = this.timePicker.split(":")[1]
            let dayStart = +func.dayStart(+new Date());
            let startTimestamp = parseInt(dayStart/1000) + hours * 60 * 60 + minutes * 60
            return this.$emit("schedule", {recurringDaily: this.recurringDaily, startTimestamp})
        }
    }
}
</script>

<style lang="sass" scoped>
.show-schedule-box
    background: #FFFFFF
</style>
