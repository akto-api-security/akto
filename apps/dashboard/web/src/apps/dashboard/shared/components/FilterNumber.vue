<template>
    <div class="filter-container">
        <div class="filter-title">
            {{title}} in last {{range[0]}} to {{range[1]}} {{unit}}
        </div>
        <v-range-slider
            v-model="range"
            :min="min"
            :max="max"
            hide-details
            @change="updatedRange"
            color="#6200EA"
            :ripple="false"
            dense
            :thumb-size="13"
        />
    </div>
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "FilterNumber",
    props: {
        min: obj.numR,
        max: obj.numR,
        title: obj.strR,
        unit: obj.strR
    },
    data () {
        return {
            range: [this.min, this.max]
        }
    },
    methods: {
        updatedRange() {
            this.$emit('clickedItem', {min: this.range[0], max: this.range[1]}) 
        }
    }

}
</script>

<style scoped lang="sass">
.filter-container
    padding: 16px 12px
    background: #FFFFFF

.filter-title
    font-size: 13px
    color: #47466A    
    padding-bottom: 20px
</style>