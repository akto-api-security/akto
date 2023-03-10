<template>
    <v-menu bottom offset-x offset-y min-width="150" max-height="300px">
        <template v-slot:activator="{on, attrs}">
            <div v-on="on" v-bind="attrs">
                <slot name="activator2"/>
            </div>
        </template>

        <v-list class="gray-menu" v-if="items && items.length > 0">
            <v-list-item 
                v-for="(item, index) in items"
                :key=index
                class='row-nav-drawer' 
                active-class="active-item" 
                @click="item.click"
            >
                <v-list-item-content class="content-nav-drawer">
          
                    <v-list-item-title class="title-nav-drawer">
                      <v-icon v-if="item.icon" size="12" color="#fff">{{ item.icon }}</v-icon>
                      {{item.label}}
                    </v-list-item-title>
                </v-list-item-content>
            </v-list-item>
        </v-list>
    </v-menu>
    
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "SimpleMenu",
    props: {
        items: obj.arrR
    },
}
</script>

<style lang="sass" scoped>
.row-nav-drawer
  min-height: 32px
  &:before
    opacity: 0 !important
  &:hover
    text-decoration: underline      
    text-decoration-color: #FFFFFF      
.content-nav-drawer
  padding: 4px 0
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
  background-color: #73728D
.active-item
  &:before
    opacity: 0 !important
  &:hover
    text-decoration: underline      
    text-decoration-color: #FFFFFF      
  
.title-nav-drawer
  color:  #FFFFFF
  margin-left: 0px !important
  font-weight: 400 !important
  font-size: 13px
</style>