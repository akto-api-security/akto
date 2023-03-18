<template>
  <v-progress-circular :size="size ? size : 12" indeterminate :color="color ? color : 'var(--themeColor)'" width="2"/>
</template>

<script>
import obj from "@/util/obj";
export default {
  name: "Spinner",
  props: {
    size: obj.numN,
    color: obj.strN
  },
}
</script>

<style scoped>

</style>