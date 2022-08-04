<template>
  <div>
    <simple-table
        :headers="endpointHeaders"
        :items="testingRunResults"
        name="API Testing Results"
        @rowClicked="openDetails"
    >
      <template #item.tests="{item}">
        <sensitive-chip-group :sensitiveTags="Array.from(item.tests || new Set())" />
      </template>
    </simple-table>

    <v-dialog
        v-model="openDetailsDialog"
    >
      <div class="details-dialog">
        <a-card
            title="Test details"
            color="rgba(33, 150, 243)"
            subtitle=""
            icon="$fas_stethoscope"
        >
          <template #title-bar>
            <v-btn
                plain
                icon
                @click="openDetailsDialog = false"
                style="margin-left: auto"
            >
              <v-icon>$fas_times</v-icon>
            </v-btn>
          </template>
          <div class="pa-4">
            <sample-data :messages='requestAndResponse'/>
          </div>
        </a-card>
      </div>

    </v-dialog>
  </div>

</template>

<script>
import obj from "@/util/obj";
import api from "@/apps/dashboard/views/testing/api";
import SensitiveChipGroup from "@/apps/dashboard/shared/components/SensitiveChipGroup";
import SampleData from '@/apps/dashboard/shared/components/SampleData'
import ACard from '@/apps/dashboard/shared/components/ACard'
import SimpleTable from "@/apps/dashboard/shared/components/SimpleTable";

export default {
  name: "TestResultsTable",
  components: {
    SensitiveChipGroup,
    ACard,
    SampleData,
    SimpleTable,
  },
  props: {
    testingRunResults: obj.arrR,
    showVulnerableOnly: obj.boolR
  },
  data() {
    return  {
      newKey: this.nonNullAuth ? this.nonNullAuth.key : null,
      newVal: this.nonNullAuth ? this.nonNullAuth.value: null,
      openDetailsDialog: false,
      requestAndResponse: null,
      endpointHeaders: [
        {
          text: "color",
          value: ""
        },
        {
          text: "Endpoint",
          value: "url"
        },
        {
          text: "Method",
          value: "method"
        },
        {
          text: "Collection",
          value: "collectionName"
        },
        {
          text: "Tests",
          value: "tests"
        },
        {
          text: "Time",
          value: "timestamp"
        }

      ]
    }
  },
  methods: {
    async openDetails(row) {
      function filterVulnerable(val, showVulnerableOnly) {
        if (!showVulnerableOnly) return true;
        return val
      }
      let r = await api.fetchRequestAndResponseForTest(row.x)
      this.requestAndResponse = r.testingRunResults && r.testingRunResults[0] ? Object.entries(r.testingRunResults[0].resultMap).filter(x => filterVulnerable(x[1].vulnerable, this.showVulnerableOnly)).map(x => {return {message: x[1].message, title: x[0], highlightPaths:[]}}) : []
      this.openDetailsDialog = true
    },
    goToEndpoint(row) {
      let routeObj = {
        name: 'apiCollection/urlAndMethod',
        params: {
          apiCollectionId: row.x.apiInfoKey.apiCollectionId,
          urlAndMethod: btoa(row.x.apiInfoKey.url+ " " + row.x.apiInfoKey.method)
        }
      }

      this.$router.push(routeObj)
    },
  }
}
</script>

<style scoped>

</style>