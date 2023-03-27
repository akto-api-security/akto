<template>
    <div style="display: flex; flex-direction: row; height: 40px; align-items: center">
        <div class="col_1">
            <p> {{ stepNumber }} </p>
        </div>
        <div style="margin-left: 12px; display: flex; align-items: center; width: 100%">
            <slot name="content"/>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"

export default {
    name: "StepBuilder",
    components: {
    },
    props: {
      stepNumber: obj.numR,
    },
    mounted () {

    },
    methods: {

    }
}
</script>

<style lang="sass" scoped>
.clickable-docs
    cursor: pointer
    color: var(--quickStartTheme) !important
    text-decoration: underline 

.col_1 
    height: 20px
    width: 20px
    border: 2px solid var(--themeColor)
    border-radius: 50%
    text-align: center
    font-style: normal
    font-weight: 600
    font-size: 14px
    line-height: 16px
    color: var(--themeColor)

.row > div:first-child 
    width: 20px

.v-text-field
    padding: 0px
    margin-top: 14px

.postman_key_input
    width: 310px

.replay-div
    display: flex
    align-items: center
    height: 40px


.radio-button-label
    font-weight: 800 !important
    font-size: 13px !important
    color: var(--hexColor19)


</style>

<style>

.v-text-field .v-input__control .v-input__slot {
    margin: 0px 4px 0px 4px !important;
    color: var(--hexColor19) !important;
}

.v-text-field input {
    color: var(--hexColor19) !important;
    font-weight: 400 !important;
    font-size: 13px !important;
}

.v-text-field .v-input__append-inner, .v-text-field .v-input__prepend-inner {
    align-self: auto;
    display: inline-flex;
    margin-top: 0px;
    line-height: 0;
    -webkit-user-select: none;
    -moz-user-select: none;
    -ms-user-select: none;
    user-select: none;
}

.v-icon__component, .v-icon__svg {
    height: 12px;
    width: 12px;
}

/* .v-application--is-ltr .v-input--selection-controls__input {
    margin-right: 8px;
} */


</style>