<template>
    <v-text-field
        v-model="newName"
        v-click-outside="{
            handler: onClickOutside,
            closeConditional
        }"
        :placeholder="placeholder"
        required autofocus dense flat
        @keydown.enter="onSubmit"
        @keydown.esc="onEscape"
        @keyup="onKeystroke"
    />
</template>

<script>
    import obj from "@/util/obj";

    export default {
        name: "SimpleTextField",
        props :{
            readOutsideClick: obj.boolR,
            placeholder: obj.strR,
            defaultName: obj.strN
        },
        data () {
            return {
                newName: this.defaultName || ''
            }
        },
        methods: {
            onSubmit(e) {
                let finalName = this.newName
                this.newName = ''
                e.stopPropagation()
                this.$emit('changed', finalName)
            },
            onKeystroke(e) {
                e.stopPropagation()
                this.$emit('onKeystroke', this.newName)
            },
            onEscape() {
                this.newName = ''
                this.$emit('aborted')
            },
            onClickOutside() {
                this.onEscape()
            },
            closeConditional(e) {
                return this.readOutsideClick
            }
        }


    }
</script>

<style scoped>
.v-text-field {
    margin-top: 0px;
}

.v-text-field >>> .v-text-field__details {
    display: none
}

.v-text-field >>> input {
    font-size: 13px
}

</style>