<template>
    <simple-menu :items="items">
        <template v-slot:activator2>
            <v-btn
                icon
                color="var(--themeColorDark)"
            >
                <div style="position: relative">
                    <v-icon size="16" class="main-icon">{{icon}}</v-icon>
                    <v-icon size="12" width="10" class="caret-icon">$fas_angle-down</v-icon>
                </div>
            </v-btn>
        </template>
    </simple-menu>
</template>

<script>
import obj from "@/util/obj"
import SimpleMenu from './SimpleMenu'

export default {
  components: { SimpleMenu },
    name: "IconMenu",
    props: {
        icon: obj.strR,
        items: obj.arrR
    },
    data () {
        return {

        }
    },
    methods: {

    }
}
</script>

<style lang="sass" scoped>
.main-icon
    position: absolute
    left: -15px
    top: -8px

.caret-icon
    position: absolute
    left: -2px
    top: -6px
</style>