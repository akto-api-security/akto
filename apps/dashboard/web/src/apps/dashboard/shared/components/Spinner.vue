<template>
  <v-progress-circular :size="size ? size : 12" indeterminate color="#6200EA" width="2"/>
</template>

<script>
import obj from "@/util/obj";
export default {
  name: "Spinner",
  props: {
    size: obj.numN
  },
}
</script>

<style scoped>

</style>