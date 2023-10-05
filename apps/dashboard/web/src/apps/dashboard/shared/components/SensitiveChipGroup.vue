<template>
    <v-chip-group active-class="primary--text" v-if="sensitiveTags">
        <v-chip v-for="tag in sensitiveTags.slice(0, 2)" :key="tag" small :color='chipColor || "var(--themeColorDark3)"'
            class="sensitive-tag-chip">
            <v-icon color="var(--white)" size="14" v-if="!hideTag">{{ getTagIcon(tag) }}</v-icon>
            <span style="color: var(--white)">{{ toTitleCase(tag) }}</span>
        </v-chip>
        <span v-if="sensitiveTags.length > 2" :color='chipColor || "var(--themeColor)"'>
            + {{ sensitiveTags.length - 2 }} more
        </span>
    </v-chip-group>
</template>

<script>

import func from "@/util/func"
import obj from "@/util/obj"

export default {
    name: "SensitiveChipGroup",
    props: {
        sensitiveTags: obj.arrR,
        chipColor: obj.strN,
        hideTag: obj.boolN
    },
    methods: {
        getTagIcon(tag) {
            return func.sensitiveTagDetails(tag)
        },
        toTitleCase(str) {
            return str.replace(
                /\w\S*/g,
                function (txt) {
                    return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
                }
            )
        }
    }
}
</script>

<style lang="sass">
.v-chip-group .v-slide-group__content
    padding: 0px !important 
    
.chip-group-container .v-slide-group__prev
    display: none    
.v-slide-group__next
    display: none        

</style>

<style scoped lang="sass">
.sensitive-tag-chip
    margin: 0px 4px !important
    padding: 4px  
    
.small-gray-text
    font-size: 12px
    color: #47466A64
    padding-top: 4px
    text-align: right
    width: 80%
    font-weight: 600
</style>