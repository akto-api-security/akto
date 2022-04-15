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
            <v-btn primary dark color="#6200EA" @click="btnClicked">
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
        itemsSearch: obj.objR,
        operationName: obj.strR,
        fetchParams: Function
    },
    data () {
        return {
            allItems: {},
            limitedItems: [],
            filters: this.itemsSearch.filters, 
            filterOperators: this.itemsSearch.filterOperators, 
            sortKey: this.itemsSearch.sortKey, 
            sortDesc: this.itemsSearch.sortDesc, 
            total: this.itemsSearch.total, 
            isSensitive: this.itemsSearch.isSensitive,
            maxN: 1000,
            currPage: 1,
            loading: false
        }
    },
    methods: {
        toFilterListObj(x) {
            let location = (x.responseCode == -1 ? 'Request' : x.responseCode) + ' ' + (x.isHeader ? 'headers' : 'payload')
            let apiCollectionName = this.mapCollectionIdToName[x.apiCollectionId]
            return {
                value: x.param + " " + location + " " + x.method + " " + x.url + " " + apiCollectionName,
                title: x.param,
                subtitle: location + " " + x.method + " " + x.url + " (" + apiCollectionName + ")"
            }
        },
        appliedFilter ({item, checked}) {
            this.allItems[item.value].selected = checked
        },
        selectedAll({items, checked}) {
            for(var index in items) {
                this.allItems[items[index].value].selected = checked
            }
        },
        fetchRecentParams() {
            let skip = (this.currPage-1)*this.maxN
            let _toFilterListObj = this.toFilterListObj
            this.fetchParams(this.sortKey, this.sortDesc? -1 : 1, skip, this.maxN, this.filters, this.filterOperators).then(resp => {
                this.loading = false
                let allII = {}
                for(let index in resp.endpoints) {
                    let item = _toFilterListObj(resp.endpoints[index])
                    allII[item.value] = item
                    item.selected = false
                }
                this.allItems = {...allII}

            }).catch(e => {
                this.loading = false
            })            
        },
        getDataFromApi () {
            this.loading = true
            this.fetchRecentParams()
        },
        calcLimitedItems() {
            this.limitedItems = Object.values(this.allItems)
        },
        btnClicked() {
            this.$emit('btnClicked', {items: this.limitedItems.filter(x => x.selected)})
        }
    },
    computed: {
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    watch: {
      currPage: {
        handler () {
          this.getDataFromApi()
        },
        deep: true,
      },
      allItems: {
          handler() {
              this.calcLimitedItems()
          }
      },
      itemsSearch: {
          handler() {
              this.allItems = []
              this.fetchRecentParams()
          },
          deep: true
      }
    },
    mounted () {
        this.fetchRecentParams()
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