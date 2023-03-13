<template>
    <v-chip-group
        active-class="primary--text"
        v-if="tags"
    >
        <v-chip
            v-for="tag in tags.slice(0,2)"
            :key="tag"
            label
            color="var(--themeColor10)"
            text-color="var(--themeColor5)"
            class="tag-chip"
        >
            #{{toTitleCase(tag)}}
        </v-chip>
        <span v-if="tags.length > 2" color="var(--themeColor)" style="margin: auto 0px">
            + {{tags.length-2}} more
        </span>
    </v-chip-group>
    
</template>

<script>

import func from "@/util/func"
import obj from "@/util/obj"

export default {
    name: "TagChipGroup",
    props: {
        tags: obj.arrR
    },
    methods: {
        toTitleCase(str) {
            return str.replace(
                /\w\S*/g,
                function(txt) {
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
</style>

<style scoped lang="sass">
.tag-chip
    margin: 0px 4px !important
    padding: 8px 8px 8px 4px 
    font-weight: 500
    font-size: 14px
</style>