<template>
    <div class="d-flex justify-space-between flex-column">
        <div class="dialog-title">Test environment settings</div>

            <div class="heading">Select logging level</div>
            <v-select
                class="form-field-text"
                :items="['INFO', 'WARN', 'ERROR']"
                label="Log Level"
                v-model="logLevel"
            ></v-select>

            <div class="heading">Select the rate of requests (per second)</div>
            <v-text-field
                class="form-field-text"
                type="number"
                label="Requests per second"
                v-model="rate"
            ></v-text-field>

            <div class="heading">Response time from your servers</div>
            <v-text-field
                class="form-field-text"
                type="number"
                label="Time in milliseconds"
                v-model="waitTime"
            ></v-text-field>

        <div>
            <v-btn color="var(--themeColor)" dark @click="completed" :disabled="false" style="float: right">
                <span class="info-text">{{btnText}}</span>
            </v-btn>
            
        </div>
    </div>
</template>

<script>

    import obj from "@/util/obj"

    export default {
        name: "TestEnvSetup",
        props: {
            btnText: obj.strR
        },
        data () {
            return {
                logLevel: 'INFO',
                rate: 100,
                waitTime: 100
            }
        },
        methods: {
            completed () {
                this.$emit('completed', {
                    logLevel: this.logLevel, 
                    rate: +this.rate, 
                    waitTime: +this.waitTime})
            }
        }
    }

</script>

<style scoped lang="sass">
.dialog-title
    font-size: 16px
    font-weight: 600
    color: var(--themeColorDark)
    margin-bottom: 16px

.heading
    font-size: 14px
    font-weight: 500
    color: var(--themeColorDark)

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