<template>
    <v-dialog
            :value="value"
            @input="closeDialog"
            transition="v-scroll-x-transition"
    >
        <div class="board-dialog">
            <div>
                <div class="pa-4 d-flex justify-space-between">
                    <div>
                        <span class="dialog-title pl-2">{{ title}}</span>
                        <span class="dialog-subtitle pl-2">{{ subtitle }}</span>
                    </div>
                    <v-btn
                            icon
                            @click="closeDialog"
                    >
                        <v-icon>$fas_times</v-icon>
                    </v-btn>
                </div>
                <div>
                    <json-viewer
                        v-if="content"
                        :contentJSON="content"
                        :errors="{}"
                        :highlightItem="highlightItem"
                    />                    
                </div>

            </div>
        </div>
    </v-dialog>
</template>

<script>
    import obj from "@/util/obj"
    import func from "@/util/func"
    import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
import { mapState } from 'vuex'

    export default {
        name: "SpecFileDialog",
        components: {
            JsonViewer
        },
        props: {
            value: obj.boolR,
            title: obj.strR,
            subtitle: obj.strN,
            highlightItem: obj.objN
        },
        data () {
            return {
                
            }
        },
        methods: {
            closeDialog() {
                this.$emit('input', false)
            }
        },
        computed: {
            ...mapState('today', ['content'])
        }
    }
</script>

<style scoped lang="sass">

.board-dialog
    position: absolute
    right: 0px
    top: 0px
    margin: 0px
    height: 100%
    opacity: 1
    width: 43%
    background-color: var(--white)
    overflow-y: scroll

.dialog-title
    font-weight: 600
    font-size: 24px
    color: var(--themeColorDark)

.dialog-subtitle
    font-weight: 600
    font-size: 16px
    color: var(--themeColorDark)
    opacity: 0.5


</style>