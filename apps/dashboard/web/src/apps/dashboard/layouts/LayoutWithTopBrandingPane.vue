<template>
    <div style="height: 100%">
        <div class="top-bar">
            <v-card-title style="flex: 0 0 70%; padding-left: 120px">
                <h1 class="display-1 page-login_title">
                  <img src="/public/favicon.svg" style="height: 30px;" />akto
                </h1>
            </v-card-title>
            <div class="actions-box">
                <slot name="actions"/>
            </div>
        </div>
        <div class="main">
            <slot name="main"/>
        </div>
    </div>
</template>

<script>
    import obj from "@/util/obj";

    export default {
        name: "LayoutWithTopBrandingPane",
        props: {
        }
    }

</script>

<style scoped lang="sass">
.top-bar
    background: white
    height: 80px
    display: flex
    flex-wrap: wrap
    width: 100%

.page-login_title
    font-weight: 600
    font-size: 30px !important
    margin-left: 5px
    color: #6200EA
    font-family: Poppins, sans-serif !important

.main
    height: 100%

.actions-box
    flex: 0 0 30%

</style>