<template>
    <div style="height: 100%">
        <div class="top-bar">
            <v-card-title class="top-bar-branding">
                <h1 class="display-1 page-login_title">
                  <img src="/public/akto_name_with_logo.svg" style="height: 42px;" />
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

.top-bar-branding
    flex: 0 0 70%
    padding-left: 112px

.page-login_title
    margin-left: 5px

.main
    height: 100%

.actions-box
    flex: 0 0 30%

</style>