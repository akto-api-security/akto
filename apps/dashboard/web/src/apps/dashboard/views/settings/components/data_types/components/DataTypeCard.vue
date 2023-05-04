<template>
    <div style="padding: 6px">
        <v-hover v-slot="{ hover }">
            <tr class="table-row d-flex" >
                <td
                    class="table-column"
                    :style="{'background-color': item.color, 'padding' : '0px !important', 'min-width':'6px'}"
                />
                <td
                    class="table-column clickable"
                    @click="dataTypeSelected(item)"
                    style="width: 100%"
                >
                    <div class="table-entry">
                        <span>{{item.prefix}}</span>
                        <span style="font-weight: bold">{{item.name}}</span>
                    </div>
                </td>

                <div v-if="actions && hover && actions.length > 0" class="table-row-actions">
                    <actions-tray :actions="actions || []" :subject=item></actions-tray>
                </div>
            </tr>
        </v-hover>
        
    </div>
</template>
        
<script>
import obj from "@/util/obj"
import ActionsTray from "@/apps/dashboard/shared/components/ActionsTray";
export default {
    name: "DataTypes",
    props: {
        item: obj.ObjR,
        actions: obj.arrN,
    },
    components: {
        ActionsTray
    },
    data() {
        return {

        }
    },
    created() {
    },
    methods: {
      dataTypeSelected(item) {
        this.$emit("selectedEntry", item)
      }
    }

}

</script>

<style lang="sass" scoped>

.table-entry
    font-size: 14px !important
    height: 100%
    text-overflow: ellipsis
    overflow : hidden
    white-space: nowrap

    &:hover
        text-overflow: clip
        white-space: normal
        word-break: break-all


.table-column
    padding: 4px 8px !important
    color: var(--themeColorDark)

.table-row
    position: relative
    background: var(--themeColorDark18)
    line-height: 40px

    &:hover
        background-color: var(--colTableBackground) !important

.table-row-actions
  padding-top: 5px !important
  position: absolute
  right: 0px

</style>