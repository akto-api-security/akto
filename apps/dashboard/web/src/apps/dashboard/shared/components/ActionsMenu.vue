<template>
    <v-menu offset-y max-height="300px" content-class="no-shadow">
        <template v-slot:activator="{on, attrs}">
            <div v-on="on" v-bind="attrs">
                <slot name="activator2"/>
            </div>
        </template>

        <div>
          <div class="arrow-up"></div>
          <div class="list-container">
            <div class="list-title d-flex align-center jc-sb" v-if="title && title.length > 0">
                {{ title }}
                <v-icon :size="14">$fas_times</v-icon>
            </div>
            <div class="item-container" v-for="(item) in items" 
                :key="item.label" 
                :style="item.isValid ? {} : {'opacity' : '0.3', 'border-top': '1px solid var(--themeColorDark10)'}"  
                @click="item.click"
            >
                <v-icon :size="12" :style="{color: item.labelColor ? item.labelColor:'var(--themeColorDark)'}">{{ item.icon }}</v-icon>
                <span class="title" :style="{color: item.labelColor ? item.labelColor : ''}">{{ item.label }}</span>
            </div>
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
        title: obj.strN,
    },
}
</script>

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
    // .arrow-up{
    //     margin-left: 88%;
    // }
</style>