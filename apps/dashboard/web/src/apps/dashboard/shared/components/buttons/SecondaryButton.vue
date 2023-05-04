<template>
    <v-btn class="show-buttons mr-2" :disabled="disabled" @click="(e) => {$emit('click', e)}">
        <span class="filter-header-span" :style="{color: color}">
            <v-icon v-if="icon" :size="16">{{icon}}</v-icon>
            {{text}}
        </span>
    </v-btn>
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "SecondaryButton",
    props: {
        click: Function,
        icon: obj.strN,
        text: obj.strR,
        disabled: obj.boolN,
        color: obj.strN
    }
}
</script>

<style lang="scss" scoped>
    
    .show-buttons{
        box-sizing: border-box !important;
        background: var(--white) !important;
        font-weight: 500 !important;
        height: 30px !important;
        display: flex !important;
        flex-direction: row !important;
        padding: 4px 8px !important;
        border:1px solid var(--hexColor22) !important;
        border-radius: 4px !important;
        box-shadow: none !important;
    }

    .filter-header-span{
        font-weight: 500;
        font-size: 12px;
        color:var(--themeColorDark);
    }
</style>