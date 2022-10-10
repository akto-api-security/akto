<template>

    <div>
        <div class="details">
            <span>Response body match: {{percentageMatch}} %</span>
        </div>
        <div style="margin: 24px">
            <sample-data :json="jsonAdvance" requestTitle="Original Request" responseTitle="Original Response"/>
        </div>
    </div>

</template>


<script>
import obj from "@/util/obj";
import SampleData from "../../../shared/components/SampleData";

export default {
  name: "TestResultDetails",
  components: {
    SampleData,
},
  props: {
    jsonAdvance: obj.objR,
    percentageMatch: obj.strR
  },
  data() {
    return  {
    }
  },
  methods: {

  },
  computed: {

  }
}
</script>

<style lang="sass" scoped>
  .details 
    padding: 24px 0px 0px 24px
    
</style>

<style scoped>
</style>

