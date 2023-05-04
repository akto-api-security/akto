<template>
    <v-card class="elevation-0 full-layout">
        <div class="akto-panes">
            <div>
                <div class="akto-right-pane">
                    <slot name="leftPane" ></slot>
                </div>
            </div>
            
            <div class="akto-left-pane">
                <slot></slot>
            </div>
            
        </div>
    </v-card>
</template>

<script>
    import obj from "@/util/obj";

    export default {
        name: "LayoutWithLeftPane",
        props: {
            title: obj.strN,
            subtitle: obj.strN
        }
    }
</script>

<style scoped lang="sass">
.full-layout
    width: -webkit-fill-available

.akto-page-title
    font-size: 24px
    font-weight: 600
    padding-bottom: 4px
    color:  var(--themeColorDark)

.akto-page-subtitle
  font-size: 13px
  font-weight: 500
  padding-bottom: 4px
  color: var(--themeColorDark5)

.akto-panes
    display: flex
    min-height: 100vh
    border-radius: 8px !important

.akto-left-pane
    padding: 16px 32px
    z-index: 1
    position: relative
    width: -webkit-fill-available
    border-radius: 0 4px 4px 0
    border: 1px solid var(--borderColor)

.akto-right-pane
    border-radius: 4px 0 0 4px
    overflow: hidden
    height: 100%
    border: 1px solid var(--borderColor)
    border-right: 0px

</style>