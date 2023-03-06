<template>
    <a-card :title="title" icon="$fas_exclamation">
        <div class="d-flex">
            <div class="pa-6">
                <donut-chart name="Total" :size="220" :data=sensitiveParams />
            </div>
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
    </a-card>
    
</template>

<script>
import ACard from '@/apps/dashboard/shared/components/ACard'
import DonutChart from '@/apps/dashboard/shared/components/DonutChart'

import obj from '@/util/obj'
import func from '@/util/func'

export default {
    name: "SensitiveParamsCard",
    components: { 
        ACard,
        DonutChart
    },
    props: {
        title: obj.strR,
        sensitiveParams: obj.arrR
    },
    methods: {
        prettify(number) {
            return func.toCommaSeparatedNumber(number)
        }
    }
}
</script>

<style lang="sass" scoped>
.legend-text
    color: var(--themeColorDark)
    font-size: 13px
    font-weight: 500

.legend-container    
    margin-top: auto
    margin-bottom: auto


</style>