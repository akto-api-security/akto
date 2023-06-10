<template>
    <v-menu offset-y min-width="150" max-height="300px" content-class="no-shadow">
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
                :style="[ item.hasLastBorder===true ? {'border-bottom': '1px solid var(--themeColorDark23)'} : {} , item.isValid ? {} : {'opacity' : '0.3' } ]"  
                @click="item.isValid ? clickFunc(item) : null"
            >
                <v-icon :size="12" color="var(--themeColorDark)" v-if="checkedMap[item.label]">$fas_check</v-icon>
                <v-icon v-else-if="item.icon" :size="12" :style="{color: item.labelColor ? item.labelColor:'var(--themeColorDark)'}">{{ item.icon }}</v-icon>
                <v-icon v-else size="12" />
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
                          <v-icon v-if="item.icon" size="12" color="var(--white)">{{ item.icon }}</v-icon>{{item.label}}
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
        selectExactlyOne: obj.boolN,
        clearFiltersValue: obj.boolN,
    },
    data() {
      return {
        checkedMap: this.items.reduce((m, i) => {
                if (i.checked) {
                    m[i.label] = true
                } else {
                    m[i.label] = false
                }
                return m
            }, {}),
        showMenu: !!this.showMenuOnDraw
      }
    },
    methods: {
        clickFunc(item){
            if(item.click)
                item.click()
            else{
                if(this.selectExactlyOne){
                  Object.keys(this.checkedMap).forEach((key) =>{
                    this.checkedMap[key] = false
                  })
                }
                this.checkedMap[item.label] = !this.checkedMap[item.label]
                this.$emit('menuClicked',{item: item, checked: this.checkedMap[item.label]})
            }
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
    },
    watch:{
      clearFiltersValue(newVal){
        if(newVal){
          Object.keys(this.checkedMap).forEach((key) =>{
            this.checkedMap[key] = false
          })
        }
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
        border: 1px solid var(--themeColorDark16) !important;
        border-radius: 8px;
        background: var(--white);
        min-width: 200px;
        .list-title{
            font-family: Poppins, sans-serif !important;
            padding: 12px 16px;
            border-radius: 7px 7px 0px 0px;
            background: var(--themeColorDark22);
            font-size: 14px;
            font-weight: 500;
            color: var(--themeColorDark);
            min-width: 235px;
        }
        .item-container {
            display: flex;
            align-items: center;
            gap: 4px;
            padding: 1px 6px;
            cursor: pointer;
            &:hover {background-color: var(--themeColorDark20)} 
            .title{
              font-family: Poppins, sans-serif !important;
                font-size: 14px !important;
                color: var(--themeColorDark);
                padding: 4px 5px;
            }
        }
    }
</style>