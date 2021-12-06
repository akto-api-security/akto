<template>
  <v-card>
    <v-card-title>
      <span>
        <v-icon class="header-icon" :color="this.color ? this.color : 'rgba(71, 70, 106, 0.5)'" v-if="this.icon">
          {{icon}}
        </v-icon>
        <span class="acard-title">{{title}}</span>
      </span>
      <slot name="title-bar"></slot>
      <v-card-subtitle>{{subtitle}}</v-card-subtitle>
    </v-card-title>
    <div :style="{height: 'calc(100% - 56px)'}">
      <slot/>
    </div>
  </v-card>
</template>

<script>
import obj from "@/util/obj"

export default {
  name: "ACard",
  props: {
    title: obj.strR,
    subtitle: obj.strN,
    icon: obj.strN,
    color: obj.strN
  }
}
</script>

<style scoped lang="sass">
.v-card
  width: -webkit-fill-available
  margin: 12px
  background: #FFFFFF
  border: 1px solid rgba(71, 70, 106, 0.2)
  border-radius: 4px
  box-shadow: none !important

.v-card__title
  display: flex
  flex-direction: row
  justify-content: space-between
  padding: 12px 16px
  font-size: 16px
  font-weight: 500
  color: rgba(71, 70, 106, 1) !important
  letter-spacing: normal
  background-color: rgba(239, 239, 239, 0.5)

.v-card__subtitle
  font-size: 12px
  font-weight: 400
  color: rgba(71, 70, 106, 0.75) !important
  padding: 0

.header-icon
  font-size: 20px !important
  margin-right: 8px

.acard-title
  letter-spacing: normal
</style>