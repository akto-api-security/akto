<template>
    <div class="show-schedule-box">
        <div class="d-flex jc-sb align-center">
            <div class="d-flex flex-column">
                <div class="d-flex" style="gap: 20px">
                    <div>
                        <simple-menu :items="hourlyTimes">
                            <template v-slot:activator2>
                                <div>
                                    <span class="column-title">Select time: </span>
                                    <span class="column-title clickable-line">{{ label }}</span>
                                    <v-icon size="14" color="var(--themeColorDark)">$fas_angle-down</v-icon>
                                </div>
                            </template>
                        </simple-menu>
                    </div>
                    <v-checkbox v-model="recurringDaily" label="Run daily" on-icon="$far_check-square"
                        off-icon="$far_square" class="run-daily-box" :ripple="false" />
                </div>
                <div class="d-flex" style="gap: 20px">
                    <div>
                        <simple-menu :items="testRunTimeOptions">
                            <template v-slot:activator2>
                                <div>
                                    <span class="column-title">Test run time: </span>
                                    <span class="column-title clickable-line">{{ testRunTimeLabel }}</span>
                                    <v-icon size="14" color="var(--themeColorDark)">$fas_angle-down</v-icon>
                                </div>
                            </template>
                        </simple-menu>
                    </div>
                    <div>
                        <simple-menu :items="maxConcurrentRequestsOptions">
                            <template v-slot:activator2>
                                <div>
                                    <span class="column-title">Max concurrent requests: </span>
                                    <span class="column-title clickable-line">{{ maxConcurrentRequestsLabel }}</span>
                                    <v-icon size="14" color="var(--themeColorDark)">$fas_angle-down</v-icon>
                                </div>
                            </template>
                        </simple-menu>
                    </div>
                </div>
                <div class="d-flex" style="gap: 20px">
                    <div>
                        <simple-menu :items="maxConcurrentRequestsOptions">
                            <template v-slot:activator2>
                                <div>
                                    <span class="column-title">Allowed requests / min: </span>
                                    <span class="column-title clickable-line">{{ maxConcurrentRequestsLabel }}</span>
                                    <v-icon size="14" color="var(--themeColorDark)">$fas_angle-down</v-icon>
                                </div>
                            </template>
                        </simple-menu>
                    </div>
                </div>
            </div>
            <v-btn primary dark color="var(--themeColor)" @click="schedule">
                {{ scheduleString }}
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
            let label = x + (x == 12 ? " noon" : " am")
            return { label, click: () => this.setHour(label, x) }
        })

        let pmTimes = hours.map(x => {
            let label = x + (x == 12 ? " midnight" : " pm")
            return { label, click: () => this.setHour(label, x + 12) }
        })

        let runTimeMinutes = hours.reduce((abc, x) => {
            if (x < 6) {
                let label = x * 10 + " minutes"
                abc.push({ label, click: () => this.setTestRunTime(label, x * 10 * 60) })
            }
            return abc
        }, [])

        let runTimeHours = hours.reduce((abc, x) => {
            if (x < 7) {
                let label = x + (x == 1 ? " hour" : " hours")
                abc.push({ label, click: () => this.setTestRunTime(label, x * 60 * 60) })
            }
            return abc
        }, [])

        let maxRequests = hours.reduce((abc, x) => {
            if (x < 11) {
                let label = x * 10
                abc.push({ label, click: () => this.setMaxConcurrentRequest(label, x * 10) })
            }
            return abc
        }, [])

        return {
            startTimestamp: func.timeNow(),
            label: "Now",
            recurringDaily: false,
            hourlyTimes: [{ label: "Now", click: () => this.setForNow() }, ...amTimes, ...pmTimes],
            testRunTime: -1,
            testRunTimeLabel: "Till complete",
            testRunTimeOptions: [{ label: "Till complete", click: () => this.setTestRunTimeDefault() }, ...runTimeMinutes, ...runTimeHours],
            maxConcurrentRequests: -1,
            maxConcurrentRequestsLabel: "Default ",
            maxConcurrentRequestsOptions: [{ label: "Default", click: () => this.setMaxConcurrentRequest("Default", -1) }, ...maxRequests]
        }
    },
    methods: {
        setHour(label, hour) {
            let dayStart = +func.dayStart(+new Date());
            this.startTimestamp = parseInt(dayStart / 1000) + hour * 60 * 60
            this.label = label
        },
        setMaxConcurrentRequest(label, requests) {
            this.maxConcurrentRequestsLabel = label
            this.maxConcurrentRequests = requests
        },
        setTestRunTimeDefault() {
            this.testRunTime = -1
            this.testRunTimeLabel = "Till complete"
        },
        setTestRunTime(label, timeInSeconds) {
            this.testRunTime = timeInSeconds,
                this.testRunTimeLabel = label
        },
        setForNow() {
            this.startTimestamp = func.timeNow()
            this.label = "Now"
        },
        schedule() {
            return this.$emit("schedule", { recurringDaily: this.recurringDaily, startTimestamp: this.startTimestamp,
                  testRunTime : this.testRunTime, maxConcurrentRequests: this.maxConcurrentRequests})
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
    background: var(--white)
    color: var(--themeColorDark)
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

.run-daily-box>>>label {
    font-size: 14px;
    font-weight: 500;
    color: var(--themeColorDark) !important;
    opacity: 1 !important
}
</style>
