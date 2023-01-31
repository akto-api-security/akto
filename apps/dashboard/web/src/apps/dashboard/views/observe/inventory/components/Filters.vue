<template>
  <div>
    <div v-for="item in groupByFilters">
      <div>
        <simple-table
            :headers=tableHeaders
            :items= item.endpoints
            :show-name=true
            :name=item.filter.name
            @rowClicked=rowClicked
            sortKeyDefault="sensitive"
            :sortDescDefault="true"
        >
          <template #item.sensitive="{item}">
            <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
          </template>
        </simple-table>
      </div>
    </div>
  </div>
</template>

<script>
import SimpleTable from "@/apps/dashboard/shared/components/SimpleTable";
import obj from "@/util/obj";
import SensitiveChipGroup from "@/apps/dashboard/shared/components/SensitiveChipGroup";

export default {
  name: "Filters",
  components: {
    SimpleTable,
    SensitiveChipGroup
  },
  props: {
    tableHeaders: obj.arrR,
    items: obj.arrR,
    filters: obj.objR
  },
  data() {
    return {
    }
  },
  methods: {
    rowClicked(row) {
      this.$emit('rowClicked', row)
    },
  },
  computed: {
    groupByFilters() {
      let a = {}
      if (!this.filters || !this.items) return a
      let b = this.filters
      this.items.forEach(x => {
        Object.keys(x["violations"]).forEach( function(key) {
          if (!a[key]) {
            a[key] = {"endpoints": [x], "filter": b[key]}
          } else {
            a[key]["endpoints"].push(x)
          }
        });
      } )
      console.log(JSON.stringify(Object.values(a)))
      return Object.values(a).reverse()
    }
  }
}
</script>

<style scoped>

</style>