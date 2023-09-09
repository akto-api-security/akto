<template>
    <div style="width: 100%">
        <div v-if="editingInProcess" style="width: 100%">
            <simple-text-field 
                :defaultName="finalName"
                :readOutsideClick="true"
                placeholder="Edit name"
                @changed="changed"
            />
        </div>
        <div v-else class="fs-12 name-editor" style="max-width: 400px">
            {{finalName}}
            <v-btn icon plain color="var(--themeColorDark)" @click="editingInProcess = true"><v-icon size="12" >$fas_edit</v-icon></v-btn>
        </div>
    </div>    
</template>

<script>

import obj from '@/util/obj'
import SimpleTextField from '../SimpleTextField'

export default {
    name: "NameInput",
    components: {
        SimpleTextField
    },
    props: {
        defaultName: obj.strR,
        defaultSuffixes: obj.arrR
    },
    data () {
        return {
            name: this.defaultName,
            editedByUser: false,
            editingInProcess: false,
            editedName: this.defaultName + "_" + this.defaultSuffixes.join("_")
        }
    },
    methods: {
        changed(name) {
            this.editedName = name
            this.editedByUser = true
            this.editingInProcess = false
            this.$emit("changed", this.finalName)
        }
    },
    computed: {
        finalName() {
            if (this.editedByUser) {
                return this.editedName
            } else {
                return [this.defaultName, ...this.defaultSuffixes].join("_")
            }
        }
    },
    mounted() {
        this.$emit("changed", this.finalName)
    },
    watch: {
        'defaultSuffixes'() {
            this.$emit("changed", this.finalName)
        }
    }
}
</script>

<style lang="sass" scoped>
.name-editor > .v-btn--icon.v-size--default
    height: 0px !important
</style>