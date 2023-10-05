<template>
    <v-menu offset-y min-width="180" max-height="300px" v-model="showMenu" content-class="no-shadow" :attach="attach ? getString: undefined">
        <template v-slot:activator="{on, attrs}">
            <div v-on="on" v-bind="attrs">
                <slot name="activator2"/>
            </div>
        </template>

        <div>
          <div :class='arrowClasses' v-if="tooltipTriangle"></div>
          <v-list class="gray-menu" v-if="items && items.length > 0">
              <v-list-item 
                  v-for="(item, index) in items"
                  :key=index
                  :class='[item.label ? "row-nav-drawer" : "separator", "e2e-accounts"]'
                  active-class="active-item" 
                  @click="item.click"
              >
                  <v-list-item-content class="content-nav-drawer">
                      <v-list-item-title class="title-nav-drawer">
                        <v-icon v-if="item.icon" size="12" color="#fff">{{ item.icon }}</v-icon>{{item.label}}
                      </v-list-item-title>
                  </v-list-item-content>
              </v-list-item>
          </v-list>

        </div>
    </v-menu>
    
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "SimpleMenu",
    props: {
        items: obj.arrR,
        tooltipTriangle: obj.strN,
        showMenuOnDraw: obj.boolN,
        extraArrowClasses: obj.arrN,
        attach: obj.strN,
    },
    data() {
      return {
        showMenu: !!this.showMenuOnDraw
      }
    },
    computed: {
      getString(){
        return '#' + this.attach
      },
      arrowClasses() {
        let ret = ["arrow-"+this.tooltipTriangle]
        if (this.extraArrowClasses) {
          ret = [...this.extraArrowClasses, ...ret]
        }
        return ret
      }
    }
}
</script>

<style lang="sass" scoped>
.row-nav-drawer
  min-height: 32px
  &:before
    opacity: 0 !important
  &:hover
    text-decoration: underline      
    text-decoration-color: var(--white)      
.content-nav-drawer
  padding: 4px 0

.separator
  height: 0px !important
  min-height: 0px !important
  max-height: 0px !important
  border-bottom: 1px solid #8F8F8F !important
  
.icon-nav-drawer
  justify-content: center
  align-self: center
  margin-top: 2px !important
  margin-bottom: 2px !important
  margin-right: 8px !important
  font-size: 13px
  & .v-icon
    color: white
    font-size: 16px
  & img
    max-width: 16px
.gray-menu
  background-color: var(--hexColor15)
  border-radius: 4px

.active-item
  &:before
    opacity: 0 !important
  &:hover
    text-decoration: underline      
    text-decoration-color: var(--white)      
  
.title-nav-drawer
  color:  var(--white)
  margin-left: 0px !important
  font-weight: 400 !important
  font-size: 13px
</style>