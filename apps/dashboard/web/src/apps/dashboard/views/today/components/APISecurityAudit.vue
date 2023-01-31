<template>
    <div>
        <fixed-columns-table
            :color="getColor()"
            title=""
            :errors="Object.values(errors || []).flat()"
            @rowClicked=rowClicked
        />    

        <spec-file-dialog
            title="API Spec"
            subtitle=""
            v-model="showSpecFileDialog"
            :highlightItem="highlightItem"
        />
    </div>
</template>

<script>
import FixedColumnsTable from '@/apps/dashboard/shared/components/FixedColumnsTable.vue'
import SpecFileDialog from './SpecFileDialog'
import func from '@/util/func'
import { mapState } from 'vuex'

export default {
    name: "APISecurityAudit",
    components: {
        FixedColumnsTable,
        SpecFileDialog
    },
    data () {
        return {
            showSpecFileDialog: false,
            highlightItem: null
        }
    },
    methods: {
        getColor() {
            return func.actionItemColors()['Total']
        },
        rowClicked(item) {
            this.highlightItem = item
            this.showSpecFileDialog = true
        }
    },
    computed: {
        ...mapState('today', ['errors'])
    }
}
</script>


<style scoped>

</style>