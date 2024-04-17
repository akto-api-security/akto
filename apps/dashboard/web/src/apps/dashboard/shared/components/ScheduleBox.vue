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
                <div class="d-flex">
                  <v-checkbox v-model="hasOverriddenTestAppUrl" label="Use different target for testing" on-icon="$far_check-square"
                              off-icon="$far_square" class="run-daily-box" :ripple="false" />

                    <v-text-field v-if="hasOverriddenTestAppUrl"
                        class="form-field-text"
                        type="text"
                        label="Override test app host"
                        v-model="overriddenTestAppUrl"
                    ></v-text-field>

                </div>
                <div class="d-flex">
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
                    <div>
                        <simple-menu :items="testRoleOptions">
                            <template v-slot:activator2>
                                <div>
                                    <span class="column-title">Select test role: </span>
                                    <span class="column-title clickable-line">{{ testRoleLabel }}</span>
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
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import api from '../api.js' 

export default {
    name: "ScheduleBox",
    components: {
        SimpleMenu,
        SimpleTextField
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
            overriddenTestAppUrl: '',
            hasOverriddenTestAppUrl: false,
            label: "Now",
            recurringDaily: false,
            hourlyTimes: [{ label: "Now", click: () => this.setForNow() }, ...amTimes, ...pmTimes],
            testRunTime: -1,
            testRunTimeLabel: "Till complete",
            testRunTimeOptions: [{ label: "Till complete", click: () => this.setTestRunTimeDefault() }, ...runTimeMinutes, ...runTimeHours],
            maxConcurrentRequests: -1,
            maxConcurrentRequestsLabel: "Default ",
            maxConcurrentRequestsOptions: [{ label: "Default", click: () => this.setMaxConcurrentRequest("Default", -1) }, ...maxRequests],
            testRoleLabel: "No test role selected",
            testRoleOptions: [],
            testRoleId: null
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
            let overriddenHost = this.hasOverriddenTestAppUrl ? this.overriddenTestAppUrl : null
            return this.$emit("schedule", { recurringDaily: this.recurringDaily, startTimestamp: this.startTimestamp,
                  testRunTime : this.testRunTime, maxConcurrentRequests: this.maxConcurrentRequests, overriddenTestAppUrl: overriddenHost, testRoleId: this.testRoleId})
        },
        setTestRole(label, id){
            this.testRoleLabel = label,
            this.testRoleId = id
        },
        fetchTestRoles(){
            api.fetchTestRoles().then(resp => {
                this.testRoleOptions.push({label: 'No test role selected', click: () => this.setTestRole("Not test role selected", "")})
                if(resp.testRoles){
                    resp.testRoles.forEach(role => {
                        this.testRoleOptions.push({label: role.name, click: () => this.setTestRole(role.name, role.hexId)})
                    })
                }
            });
        }
    },
    mounted() {
        this.fetchTestRoles()
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


<style scoped lang="sass">
.form-field-text
  padding-top: 0px !important
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
  font-size: 12px;
  color: var(--themeColorDark) !important;
}

.form-field-text >>> .v-text-field__details {
  display: none !important;
}
.form-field-text >>> .v-input__slot {
  margin-bottom: 0px !important;
}


.run-daily-box >>> .v-text-field__details {
  display: none !important;
}
.run-daily-box >>> .v-input__slot {
  margin-bottom: 0px !important;
}
</style>
