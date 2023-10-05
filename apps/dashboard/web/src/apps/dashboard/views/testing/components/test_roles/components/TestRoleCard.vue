<template>
    <div style="padding: 6px">
        <v-hover v-slot="{ hover }">
            <tr class="table-row d-flex">
                <td class="table-column"
                    :style="{ 'background-color': item.color, 'padding': '0px !important', 'min-width': '6px' }" />
                <td class="table-column clickable" @click="dataTypeSelected(item)" style="width: 100%">
                    <div class="table-entry">
                        <div style="font-weight: bold">{{ item.name }}</div>
                        <div style="font-weight: bold">
                            <div v-if="actions && hover && actions.length > 0" class="table-row-actions">
                                <actions-tray :actions="actions || []" :subject=item></actions-tray>
                            </div>
                        </div>
                    </div>
                </td>
            </tr>
        </v-hover>

    </div>
</template>
        
<script>
import obj from "@/util/obj"
import ActionsTray from "@/apps/dashboard/shared/components/ActionsTray";
import api from "../api.js"

export default {
    name: "TestRoleCard",
    props: {
        item: obj.ObjR
    },
    components: {
        ActionsTray
    },
    data() {
        return {
            actions: [
                {
                    isValid: item => true,
                    icon: item => '$fas_trash',
                    text: item => 'Delete',
                    func: item => this.deleteTestRole(item),
                    success: (resp, item) => this.toggleSuccessFunc(resp, item),
                    failure: (err, item) => this.toggleFailureFunc(err, item)
                }
            ]
        }
    },
    created() {
    },
    methods: {
        dataTypeSelected(item) {
            this.$emit("selectedEntry", item)
        },
        async deleteTestRole(item) {
            await api.deleteTestRole(item.name)
        },
        toggleSuccessFunc(resp, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${item.name}` + ` deleted successfully!`,
                color: 'green'
            })
            this.$emit('refreshRoles')
        },
        toggleFailureFunc(err, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `Error occurred while deleting ${item.name}`,
                color: 'red'
            })
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
    display: flex
    justify-content: space-between

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