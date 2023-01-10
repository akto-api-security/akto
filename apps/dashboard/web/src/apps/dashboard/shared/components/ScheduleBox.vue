<template>
    <div class="show-schedule-box">
      <div class="d-flex jc-sb">
        <div class="d-flex" style="gap: 20px">
            <div>
                <simple-menu :items="hourlyTimes">
                    <template v-slot:activator2>
                        <div>
                            <span class="column-title">Select time: </span>
                            <span class="column-title clickable-line">{{label}}</span>
                            <v-icon size="14" color="#47466A">$fas_angle-down</v-icon>
                        </div>
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
        <v-btn primary dark color="#6200EA" @click="schedule">
            {{scheduleString}}
        </v-btn>

      </div>
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
        let amTimes = hours.map(x => {
            let label = x + (x==12? " noon": " am")
            return {label, click: () => this.setHour(label, x)}
        })

        let pmTimes = hours.map(x => {
            let label =  x + (x==12? " midnight": " pm")
            return {label, click: () => this.setHour(label, x+12)}
        })
        
        return {
            startTimestamp: func.timeNow(),
            label: "Now",
            recurringDaily: false,
            hourlyTimes: [{label: "Now", click: () => this.setForNow()}, ...amTimes, ...pmTimes]
        }
    },
    methods: {
        setHour(label, hour) {
            let dayStart = +func.dayStart(+new Date());
            this.startTimestamp = parseInt(dayStart/1000) + hour * 60 * 60
            this.label = label
        },
        setForNow() {
            this.startTimestamp = func.timeNow()
            this.label = "Now"
        },
        schedule() {
            return this.$emit("schedule", {recurringDaily: this.recurringDaily, startTimestamp: this.startTimestamp})
        }
    },
    computed: {
        scheduleString() {
            if (this.label === "Now") {
                if (this.recurringDaily) {
                    return "Run daily at this time"
                } else {
                    return "Run once now"
                }
            } else {
                if (this.recurringDaily) {
                    return "Run daily at " + this.label
                } else {
                    return "Run today at " + this.label
                }
            }
        }
    }
}
</script>

<style lang="sass" scoped>
.show-schedule-box
    background: #FFFFFF
    color: #47466A
    margin-top: 16px

.column-title
    font-size: 14px
    font-weight: 500
    
</style>

<style scoped>
.v-input--selection-controls {
    padding-top: 0px !important;
    margin-top: 0px !important;
}

.run-daily-box >>> label {
    font-size: 14px;
    font-weight: 500;
    color: #47466A !important;
    opacity: 1 !important

}

</style>
