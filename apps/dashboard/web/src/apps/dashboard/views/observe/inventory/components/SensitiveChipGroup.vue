<template>
    <v-chip-group
        active-class="primary--text"
        v-if="sensitiveTags"
    >
        <v-chip
            v-for="tag in sensitiveTags.slice(0,2)"
            :key="tag"
            small
            outlined
            label
            color="#6200EA"
            class="sensitive-tag-chip"
        >
            <v-icon color="#6200EA">{{getTagIcon(tag)}}</v-icon>
            <span style="color: #6200EA">{{ tag }}</span>
        </v-chip>
        <span v-if="sensitiveTags.length > 2" color="#6200EA">
            + {{sensitiveTags.length-2}} more
        </span>
    </v-chip-group>
    
</template>

<script>

import func from "@/util/func"
import obj from "@/util/obj"

export default {
    name: "SensitiveChipGroup",
    props: {
        sensitiveTags: obj.arrR
    },
    methods: {
        getTagIcon(tag) {
            return func.sensitiveTagDetails(tag)
        }        
    }
}
</script>

<style lang="sass">
.v-chip-group .v-slide-group__content
    padding: 0px !important   
</style>

<style scoped lang="sass">
.sensitive-tag-chip
    margin: 0px 4px !important
    padding: 4px
</style>