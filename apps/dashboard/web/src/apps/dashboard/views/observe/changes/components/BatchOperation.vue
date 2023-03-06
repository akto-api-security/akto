<template>
    <div class="container">
        <v-pagination
            v-model="currPage"
            :length="total < maxN ? 1 : (Math.floor(total/maxN) + (total % maxN == 0 ? 0 : 1))"
            :total-visible="3"
        />
        <filter-list 
            v-if="limitedItems && limitedItems.length > 0"
            :title="title"
            :items="limitedItems" 
            :hideOperators="true"
            @clickedItem="appliedFilter" 
            @selectedAll="selectedAll" 
            width="600px"
        />

        <div v-if="limitedItems.filter(x => x.selected).length >= maxN" class="info-text">
            <v-icon size="12">$fas_exclamation</v-icon>
            You have selected more than {{maxN}} params. We will limit the operation to the params shown in the above list. 
            You can come back and select more from other pages.
        </div>

        <div class="d-flex jc-end pa-3">
            <v-btn primary dark color="var(--themeColor)" @click="btnClicked">
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
        return {
            maxN: 1000,
            currPage: 1,
            loading: false
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
        },
        btnClicked() {
            this.$emit('btnClicked', {items: this.limitedItems.filter(x => x.selected)})
        }
    },
    computed: {
        allItems () {
            return this.items.reduce((z, e) => {
                z[e.id] = {
                    title: e.displayName,
                    value: e.id
                }
                return z
            }, {})
        },
        limitedItems() {
            return Object.values(this.allItems)
        },
        total() {
            return this.items.length
        }
    }
}
</script>

<style lang="sass" scoped>
.container
    background: var(--white)

.info-text
    padding-top: 12px
    font-size: 12px
    color: var(--themeColorDark)

</style>