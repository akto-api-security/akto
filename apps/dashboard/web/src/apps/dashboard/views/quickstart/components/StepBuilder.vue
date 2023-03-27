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

</style>
