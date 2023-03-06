<template>
    <div>
        <json-view 
            :data="contentJSON"   
            :errors="errors"  
            :maxDepth="1"
            :highlightItem="highlightItem"
            :highlightItemMap="highlightItemMap"
            class="customStyle"
            @selected="selectedElem"
        />
    </div>
            

</template>

<script>
import JSONView from "./jsontree/JSONView.vue";

import obj from '@/util/obj'

export default {
    name: "JsonViewer",
    components: {
        "json-view": JSONView
    },
    props: {
        contentJSON: obj.objR,
        errors: obj.objR,
        highlightItem: obj.objN,
        highlightItemMap: obj.objN
    },
    methods: {
        selectedElem (e) {

        }
    }
}
</script>

<style lang="sass">
.chevron-arrow
    border-right-width: 2px !important
    border-bottom-width: 2px !important

.customStyle
    --vjc-valueKey-color: green !important
    --vjc-key-color: var(--themeColor) !important

.value-key 
    font-weight: unset !important

.root-item    
    font-size: 13px !important

.properties
    opacity: 1.0 !important 
    color: var(--redMetric)

.v-file-input__text
    font-size: 13px
    color: var(--themeColorDark)


</style>
