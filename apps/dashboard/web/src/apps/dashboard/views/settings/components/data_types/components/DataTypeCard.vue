<template>
    <div style="padding: 6px">
        <v-hover v-slot="{ hover }">
            <tr class="table-row d-flex" >
                <td
                    class="table-column"
                    :style="{'background-color': item.color, 'padding' : '0px !important', 'width':'6px'}"
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

                <!-- <div v-if="actions && hover && actions.length > 0" class="table-row-actions">
                    <actions-tray :actions="actions || []" :subject=item></actions-tray>
                </div> -->
            </tr>
        </v-hover>
        
    </div>
</template>
        
<script>
import obj from "@/util/obj"
export default {
    name: "DataTypes",
    props: {
        item: obj.ObjR
    },
    components: {
    },
    data() {
        return {

        }
    },
    created() {
    },
    methods: {
      dataTypeSelected(item) {
        this.$store.state.data_types.data_type = item
      }
    }

}

</script>

<style lang="sass" scoped>

.table-entry
    font-size: 14px !important
    height: 100%

.table-column
    padding: 4px 8px !important
    border-top: 1px solid #FFFFFF !important
    border-bottom: 1px solid #FFFFFF !important
    color: #47466A

.table-row
    border: 0px solid #FFFFFF !important
    position: relative
    background: rgba(71, 70, 106, 0.03)
    height: 50px
    line-height: 40px

    &:hover
        background-color: #edecf0 !important

</style>