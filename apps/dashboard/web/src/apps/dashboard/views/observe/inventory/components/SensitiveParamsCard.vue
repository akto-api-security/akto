<template>
    <a-card :title="title" icon="$fas_exclamation">
        <div class="d-flex">
            <div class="pa-6">
                <donut-chart name="Total" :size="220" :data=sensitiveParams />
            </div>
            <div class="legend-container ml-4">
                <div v-for="(item, index) in sensitiveParams" :key="index" class="pa-2">
                    <v-avatar
                    :color="item.color"
                    :size="26"
                    v-bind="{}"
                    v-on="{}"
                    >
                        <span class="legend-text white--text">{{item.y}}</span>
                    </v-avatar>
                    <span class="legend-text ml-2">{{item.name}}</span>
                </div>
            </div>
        </div>
    </a-card>
    
</template>

<script>
import ACard from '@/apps/dashboard/shared/components/ACard'
import DonutChart from '@/apps/dashboard/shared/components/DonutChart'

import obj from '@/util/obj'

export default {
    name: "SensitiveParamsCard",
    components: { 
        ACard,
        DonutChart
    },
    props: {
        title: obj.strR,
        sensitiveParams: obj.arrR
    }
}
</script>

<style lang="sass" scoped>
.legend-text
    color: #47466A
    font-size: 13px
    font-weight: 500

.legend-container    
    margin-top: auto
    margin-bottom: auto


</style>