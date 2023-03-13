<template>
  <v-card>
    <v-card-title>
      <span>
        <v-icon class="header-icon" :color="this.color ? this.color : 'var(--themeColorDark9)'" v-if="this.icon">
          {{icon}}
        </v-icon>
        <span class="acard-title">{{title}}</span>
      </span>
      <slot name="title-bar"></slot>
      <v-card-subtitle>{{subtitle}}</v-card-subtitle>
      <span>
        <v-btn icon @click="$emit('icon_right_clicked')"  v-if="this.icon_right">
          <v-icon :color="this.icon_right_color ? this.icon_right_color : 'var(--themeColorDark9)'">
            {{icon_right}}
          </v-icon>
        </v-btn>
      </span>
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
    color: obj.strN,
    icon_right: obj.strN,
    icon_right_color: obj.strN
  }
}
</script>

<style scoped lang="sass">
.v-card
  width: -webkit-fill-available
  margin: 12px
  background: var(--white)
  border: 1px solid var(--themeColorDark13)
  border-radius: 4px
  box-shadow: none !important

.v-card__title
  display: flex
  flex-direction: row
  justify-content: space-between
  padding: 12px 16px
  font-size: 16px
  font-weight: 500
  color: var(--themeColorDark) !important
  letter-spacing: normal
  background-color: var(--rgbaColor6)

.v-card__subtitle
  font-size: 12px
  font-weight: 400
  color: var(--themeColorDark4) !important
  padding: 0

.header-icon
  font-size: 20px !important
  margin-right: 8px

.acard-title
  letter-spacing: normal
</style>