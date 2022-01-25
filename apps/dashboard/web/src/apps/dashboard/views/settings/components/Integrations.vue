<template>
    <div class="pa-4">
      <postman/>
      <v-divider></v-divider>
      <burp/>


    </div>

</template>

<script>

import Postman from "./integrations/Postman.vue"
import Burp from "./integrations/Burp.vue"

export default {
    name: "Integrations",
    components: {
      Postman, Burp
    },
    data () {
      return {

      }
    },
    methods: {},

}
</script>

<style>

</style>