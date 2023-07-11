<template>
    <div class="d-flex jc-end">
        <div class="fd-column jc-sa">
            <v-icon color="var(--themeColor)" size="12" class="mr-2">$fas_search</v-icon>
        </div>
        <div style="width: 400px">
            <simple-text-field 
                :defaultName="finalName"
                :readOutsideClick="true"
                :placeholder=placeholder
                @changed="changed"
                @onKeystroke="onKeystroke"
                :key="finalName"
            />
        </div>
    </div>


</template>

<script>

import SimpleTextField from '../SimpleTextField'
import obj from '@/util/obj'

export default {
    name: "Search",
    components: {
        SimpleTextField
    },
    props: {
        placeholder: obj.strR
    },
    data () {
        return {
            finalName: ''
        }
    },
    methods: {
        changed(newName) {
            this.$emit('changed', newName)
            this.finalName = newName
        },
        onKeystroke(value) {
            this.$emit('onKeystroke', value)
            this.finalName = value
        }
    }
}
</script>

<style lang="sass" scoped>

</style>