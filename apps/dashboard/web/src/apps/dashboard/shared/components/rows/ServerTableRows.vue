<template>
    <tr
        :class="['table-row', index == currRowIndex ? 'highlight-row' : '']"
    >
        <td
            class="table-column"
            :style="{'background-color':item.color, 'padding' : '0px !important', 'width': item.width, 'height': dense ? '24px !important' : '48px'}"
        />
        <td 
            v-for="(header, ii) in headers.slice(1)"
            :key="ii"
            class="table-column clickable"
            @click="($event) => clickRow(index, $event)"
            :style="{'height': dense ? '24px !important' : '48px'}"
        >
            <slot :name="[`item.${header.value}`]" :item="item">
                <div class="table-entry">{{item[header.value]}}</div>
            </slot>
        </td>

        <div v-if="actions && actions.length > 0 && actionsFunction(item).length > 0" class="table-row-actions">
            <simple-menu :items="actionsFunction(item)" tooltipTriangle="up" :extraArrowClasses="['mlp-74']">
                <template v-slot:activator2>
                    <v-btn icon :ripple="false" @click="showRowFunctions">
                        <v-icon size="12">$fas_ellipsis-h</v-icon>
                    </v-btn>                    
                </template>
            </simple-menu>
        </div>
    </tr>
</template>

<script>

import obj from "@/util/obj"
import SimpleMenu from '../SimpleMenu.vue'
export default {
    name:"ServerTableRows",
    components:{
        SimpleMenu
    },
    props:{
        item:obj.objR,
        actions:obj.arrN,
        headers: obj.arrR,
        index:obj.numR,
        currRowIndex:obj.numR,
        dense:obj.boolN,
    },
    methods:{
        actionsFunction(item){
            let arrayActions = []
            this.actions.forEach(action => {
                if(action.isValid(item)){
                    arrayActions.push({label:action.text(item) ,icon:action.icon(item), click: () => action.func(item)})
                }
            })
            return arrayActions
        },
        clickRow(index, $event){
            this.$emit('clickRow',index, $event)
        },
        showRowFunctions(index) {
            this.$emit('highlightRow', index)
        }
    }
}
</script>
<style lang="scss" scoped>
    .table-row{
        border: 0px solid var(--white) !important;
        position: relative;
    }
    .table-row-actions{
        position: absolute;
        right: 5px;
        top: 10%;
        opacity: 1;
    }
    .table-column{
        padding: 4px 8px !important ;
        border-top: 1px solid var(--white) !important;
        border-bottom: 1px solid var(--white) !important;
        background: var(--themeColorDark18);
        color: var(--themeColorDark);
        max-width: 250px;
        text-overflow: ellipsis;
        overflow : hidden;
        white-space: nowrap;

        :hover{
            text-overflow: clip;
            white-space: normal;
            word-break: break-all;
        }
    }
    .table-entry{
        font-size: 12px;
    }
    .highlight-row{
        background-color: var(--themeColorDark14);
    }
    
</style>