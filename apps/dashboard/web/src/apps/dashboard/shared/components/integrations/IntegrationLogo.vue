<template>
  <v-btn
    tile
    height="110px"
    width="110px"
    @click="$emit('clicked')"
    class="integration-container"
    elevation="0"
  >
    <div class="integration-details">
      <div class="integration-logo">
        <v-icon :size="50" :color="color">{{icon}}</v-icon>
      </div>
      <div class="integration-name">
        {{name}}
      </div>
    </div>
  </v-btn>
</template>

<script>
import obj from "@/util/obj";
import func from "@/util/func";

export default {
  name: "IntegrationLogo",
  props: {
    name: obj.strR
  },
  data () {
    let iconDetails = func.icon(func.sourceNum(this.name))
    return {
      icon: iconDetails.name,
      color: iconDetails.color
    }
  }
}
</script>

<style scoped lang="sass">
.integration-container
  height: 110px
  width: 110px
  padding: 8px
  margin-right: 16px
  background-color: #FFFFFF !important

.integration-details
  display: flex
  flex-direction: column
  justify-content: space-between
  height: 100px

.integration-logo
  margin: auto

.integration-name
  font-weight: 400
  font-size: 14px
  color: #47466A
  margin: auto

</style>