<template>
    <v-menu offset-y min-width="150" max-height="300px" v-model="showMenu" content-class="no-shadow">
        <template v-slot:activator="{on, attrs}">
            <div v-on="on" v-bind="attrs">
                <slot name="activator2"/>
            </div>
        </template>

        <div>
          <div :class='arrowClasses' v-if="tooltipTriangle"></div>
          <div class="list-container" v-if="newView">
            <div class="list-title d-flex align-center jc-sb" v-if="title && title.length > 0">
                {{ title }}
                <v-icon :size="14" :style="{cursor : 'pointer'}">$fas_times</v-icon>
            </div>
            <div class="item-container" v-for="(item,index) in items" 
                :key="index" 
                :style="item.isValid ? {} : {'opacity' : '0.3', 'border-top': '1px solid var(--themeColorDark10)'}"  
                @click="item.isValid ? clickFunc(item) : null"
            >
                <v-icon v-if="item.icon" :size="12" :style="{color: item.labelColor ? item.labelColor:'var(--themeColorDark)'}">{{ item.icon }}</v-icon>
                <span class="title" :style="{color: item.labelColor ? item.labelColor : ''}">{{ item.label }}</span>
            </div>
          </div>
          <div v-else>
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
                          <v-icon v-if="item.icon" size="12" color="#fff">{{ item.icon }}</v-icon>{{item.label}}
                        </v-list-item-title>
                    </v-list-item-content>
                </v-list-item>
            </v-list>
          </div>
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
        newView: obj.boolN,
        title: obj.strN,
    },
    data() {
      return {
        showMenu: !!this.showMenuOnDraw
      }
    },
    methods: {
        clickFunc(item){
            if(item.click)
                item.click()
            else
                this.$emit('menuClicked',item)
        }
    },
    computed: {
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
<style lang="scss" scoped>
    .list-container{
        border: 1px solid var(--themeColorDark16);
        border-radius: 8px;
        background: var(--white);
        min-width: 200px;
        min-height: 130px;
        .list-title{
            padding: 12px 16px;
            background: #47466A14;
            font-size: 14px;
            color: var(--themeColorDark);
        }
        .item-container {
            display: flex;
            align-items: center;
            gap: 4px;
            margin: 10px 0px;
            padding: 0 14px;
            cursor: pointer;
            .title{
                font-size: 16px !important;
                color: var(--themeColorDark);
            }
        }
    }
</style>