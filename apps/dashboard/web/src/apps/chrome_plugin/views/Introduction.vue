<template>
    <div class="endpoints-container">
        <div class="info-container">
            <div>
                <div class="info-title">{{websiteHostName}}</div>
                <div class="info-subtitle">Web application</div>
            </div>
            <v-spacer/>
            <div>
                <div class="info-title">{{Object.entries(endpoints).length}}</div>
                <div class="info-subtitle">endpoints discovered</div>
            </div>
        </div>    

        <div class="info-container">
            <donut-chart name="Total" :size="100" :data=sensitiveParams />
            <v-spacer/>
            <div class="legend-container ml-8">
                <div v-for="(item, index) in sensitiveParams" :key="index" class="pa-2">
                    <v-avatar
                    :color="item.color"
                    :size="13"
                    v-bind="{}"
                    v-on="{}"
                    >
                        <span class="legend-text white--text"></span>
                    </v-avatar>
                    <span class="legend-text ml-2">{{item.name}} <span class="grey-text">({{prettify(item.y)}})</span></span>
                </div>
            </div>
        </div>

        <div class="info-container" style="flex-direction: column; max-height: 200px; overflow: scroll">
            <div v-for="(item, index) in endpointsForTable" :key="index" class="endpoint-container">
                <div class="fill-color"> </div>
                <div class="ma-2 d-flex" style="width: 100%">
                    <div style="width: 60%; margin: auto 0">
                        <div class="info-title">{{item.method}} {{item.endpoint}}</div>
                        <!-- <div class="info-subtitle" :style='{"color": item.authType === "Unauthenticated" ? "#f44336" : "#00bfa5"}'>{{item.authType}}</div> -->
                    </div>
                    <div style="max-width: 40%; margin: auto 0">
                        <sensitive-chip-group :sensitiveTags="[...item.type]" />
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>

    import func from '@/util/func'
    import DonutChart from '@/apps/dashboard/shared/components/DonutChart'
    import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
    import {mapState} from 'vuex'

    export default {
        name: 'PageIntroduction',
        components: {
            DonutChart,
            SensitiveChipGroup
        },
        data () {
            return {
                sensitiveParams1: [
                    {
                        name: "Email",
                        y: 15,
                        color: "#6200EAFF"
                    },
                    {
                        name: "Phone number",
                        y: 13,
                        color: "#6200EADF"
                    },
                    {
                        name: "Credit card",
                        y: 2,
                        color: "#6200EABF"
                    }
                ],
                headers: [
                    {
                        text: '',
                        value: 'color'
                    },
                    {
                        text: 'Endpoint',
                        value: 'endpoint'
                    },
                    {
                        text: 'Method',
                        value: 'method'
                    },
                    {
                        text: 'Sensitive params',
                        value: 'type'
                    }
                ]
            }
        },
        methods : {
            prettify(number) {
                return func.toCommaSeparatedNumber(number)
            },
            colors(i) {
                let allColors = ["#6200EAFF", "#6200EADF", "#6200EABF", "#6200EA9F", "#6200EA7F", "#6200EA5F", "#6200EA3F", "#6200EA1F"]
                return allColors[i%allColors.length]
            }
        },
        computed: {
            ...mapState('endpoints', ['endpoints', 'websiteHostName']),            
            endpointsForTable() {
                return Object.values(this.endpoints)
            },
            sensitiveParams() {
                let allTypes = Object.values(this.endpoints || {}).map(x => x.type).flat() || []
                let ret = Object.values(allTypes.reduce((z, e) => {
                    let m = z[e]
                    if (m == null) {
                        m = {
                            name: e,
                            y: 0
                        }
                        z[e] = m
                    }
                    z[e].y ++
                    return z
                }, {}))
                ret.forEach((x, index) => {
                    x.color = this.colors(index)
                })
                return ret
            }
        }
    }
</script>

<style>
    @import url('https://fonts.googleapis.com/css?family=Poppins:400,500,600&display=swap');
    .v-application {
        font-family: Poppins, sans-serif !important;
        color: #2d2434;
        letter-spacing: unset !important;
    }

    .clickable {
        cursor: pointer;
    }

</style>

<style lang="sass">
.endpoints-container
    background: rgba(71, 70, 106, 0.1)
    padding: 4px 0px

.info-container
    background: #FFFFFF
    border-radius: 8px
    padding: 8px 8px
    margin: 8px 8px
    display: flex

.info-subtitle
    font-weight: 300
    font-size: 10px
    margin-top: 2px

.info-title
    font-weight: 600
    font-size: 14px
    text-overflow: ellipsis
    overflow : hidden
    white-space: nowrap

    &:hover
        text-overflow: clip
        white-space: normal
        word-break: break-all


.legend-container
    display: flex
    justify-content: space-around
    flex-direction: column
    width: 60%

.legend-text        
    font-size: 12px

.endpoint-container
    margin: 5px 0px
    display: flex
    height: 52px
    background: rgba(71, 70, 106, 0.1)
    

.fill-color
    min-width: 4px
    max-width: 4px
    background-color: #f44336B9
</style>