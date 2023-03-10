<template>
    <v-card class="elevation-0 full-layout">
        <div class="akto-panes">
            <v-expand-transition>
                <div class="akto-left-pane" :style="{'width' : showRightPane ? '78%' : '98%', 'transition': 'all 0.2s linear'}">
                    <span class="hide-right-pane clickable" @click="toggleRightPane">
                        <v-icon v-if="showRightPane" color="var(--themeColor)">$fas_angle-right</v-icon>
                        <v-icon v-else color="var(--themeColor)">$fas_angle-left</v-icon>
                    </span>

                    <div>
                      <div>
                        <div class="akto-page-title" v-if="title">{{title}}</div>
                        <div class="akto-page-subtitle" v-if="subtitle">{{subtitle}}</div>
                      </div>
                      <slot name="universal-ctas"></slot>
                    </div>
                    <slot></slot>
                </div>
            </v-expand-transition>
            <div class="akto-right-pane" v-show="showRightPane" :style="{'width': '22%'}">
                <slot name="rightPane" ></slot>
            </div>
        </div>
    </v-card>
</template>

<script>
    import obj from "@/util/obj";

    export default {
        name: "LayoutWithRightPane",
        props: {
            title: obj.strN,
            subtitle: obj.strN
        },
        data () {
            return {
                showRightPane: true
            }
        },
        methods: {
            toggleRightPane () {
                this.showRightPane = !this.showRightPane
                this.$emit('paneAdjusted', this.showRightPane)
            },
            adjustIcon () {
                if (this.showRightPane) {
                    return {}
                } else {
                    return {
                        'transform': 'rotate(180)'
                    }
                }
            }
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

.akto-left-pane
    padding: 16px 32px
    box-shadow: 4px -5px 8px var(--rgbaColor5)
    z-index: 1
    position: relative

.hide-right-pane
    position: absolute
    top: 80px
    right: -10px
    border: 1px solid var(--borderColor)
    box-sizing: border-box
    box-shadow: 2px 2px 4px var(--borderColor)
    border-radius: 8px
    background-color: var(--white)
    z-index: 2
    min-width: 24px
    justify-content: space-around
    display: flex


</style>