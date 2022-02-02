<template>
    <div class="container">
        <filter-list :title="title" :items="items" @clickedItem="appliedFilter" @selectedAll="selectedAll" width="600px"/>

        <div v-if="limitedItems.length < Object.values(allItems).filter(x => x.selected).length" class="info-text">
            <v-icon size="12">$fas_exclamation</v-icon>
            You have selected {{Object.values(allItems).filter(x => x.selected).length}} items. 
            Only {{maxN}} items are allowed for this operation. 
            We will limit the operation to first {{maxN}} items.
        </div>

        <div class="d-flex jc-end pa-3">
            <v-btn primary dark color="#6200EA" @click="$emit('btnClicked', {items: limitedItems})">
                {{operationName}}
            </v-btn>
        </div>
    </div>
</template>

<script>

import obj from "@/util/obj"
import FilterList from "@/apps/dashboard/shared/components/FilterList"

export default {
    name: "BatchOperation",
    components: { 
        FilterList 
    },
    props: {
        title: obj.strR,
        items: obj.arrR,
        operationName: obj.strR
    },
    data () {
        let allItems = {}
        for(let index in this.items) {
            let item = this.items[index]
            allItems[item.value] = item
            item.selected = false
        }
        return {
            allItems: allItems,
            maxN: 100
        }
    },
    methods: {
        appliedFilter ({item, checked}) {
            this.allItems[item.value].selected = checked
        },
        selectedAll({items, checked}) {
            for(var index in items) {
                this.allItems[items[index].value].selected = checked
            }
        }
    },
    computed: {
        limitedItems() {
            return Object.values(this.allItems).filter(x => x.selected).slice(0, this.maxN)
        }
    }
}
</script>

<style lang="sass" scoped>
.container
    background: #FFFFFF

.info-text
    padding-top: 12px
    font-size: 12px
    color: #47466A

</style>