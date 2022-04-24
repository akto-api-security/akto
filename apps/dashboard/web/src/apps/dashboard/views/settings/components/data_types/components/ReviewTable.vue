<template>
    <div style="padding-top: 60px">
      <simple-table
          :headers=tableHeaders
          :items=reviewData
          name="Sensitive"
          :hide-download-c-s-v-icon="true"
      >
      </simple-table>
    </div>
</template>

<script>
import constants from "@/util/constants";
import SimpleTable from "@/apps/dashboard/shared/components/SimpleTable";
import obj from "@/util/obj";

export default {
    name: "ReviewTable.vue",
    components: {
      SimpleTable
    },

    props: {
      reviewData: obj.arrR
    },

    data() {
        return {
          tableHeaders: [
              {
                text: 'Collection Id',
                value: 'apiCollectionId'
              },
              {
                text: 'Endpoint',
                value: 'url'
              },
              {
                text: 'Method',
                value: 'method'
              },
              {
                text: 'Key',
                value: 'key'
              },
              {
                text: 'Value',
                value: 'value'
              },
          ],
      }
    }
}
</script>

<style scoped>

</style>