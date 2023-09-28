<template>
    <div>
        <div v-if="getType() === 'INTEGER'">
            <filter-number
                :min="getItems().min"
                :max="getItems().max"
                :title="title"
                unit="days"
                @clickedItem="clickedItem({...$event, type: getType()})"
            />
        </div>
        <div v-if="getType() === 'STRING'">
            <filter-list
                :title="title" 
                :items="getItems()" 
                @clickedItem="clickedItem({...$event, type: getType()})" 
                @operatorChanged="operatorChanged({...$event, type: getType()})"
                @selectedAll="selectedAll({...$event, type: getType()})"
                :listOperators="listOperators"
            />
        </div>
        <div v-if="getType() === 'SEARCH'">
            <filter-search
                :title="title" 
                @clickedItem="clickedItem({...$event, type: getType()})" 
                @operatorChanged="operatorChanged({...$event, type: getType()})"
            />
        </div>
    </div>
</template>

<script>

import obj from "@/util/obj"
import FilterNumber from './FilterNumber.vue'
import FilterList from './FilterList.vue'
import FilterSearch from './FilterSearch.vue'

export default {
    name: "FilterColumn",
    components: { 
        FilterNumber, 
        FilterList,
        FilterSearch
    },
    props: {
        title: obj.strR,
        typeAndItems: obj.objR,
        listOperators: obj.arrN
    },
    methods: {
        getType() {
            return this.typeAndItems.type
        },
        getItems() {
            return this.typeAndItems.values
        },
        clickedItem(e) {
            this.$emit('clickedItem', e)
        },
        operatorChanged(e) {
            this.$emit('operatorChanged', e)
        },
        selectedAll(e) {
            this.$emit('selectedAll', e)
        }
    }
    
}
</script>