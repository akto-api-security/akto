<template>
    <v-card :outlined="true" class="test-suite-card pa-3" @click="clicked" :class="{'selected': (selectedTestSuite === id)}">
        <div class="test-suite-title-box">
            <div class="test-suite-title">
                {{ title }}
            </div>
            <hint-icon :value="description"/>
        </div>
        <div class="test-suite-subtitle">
            {{ subtitle }}
        </div>
    </v-card>
</template>

<script>
import obj from "@/util/obj";
import {mapState} from 'vuex'
import HintIcon from "./HintIcon";

export default {
    name: "TestSuiteCard",
    props: {
        title: obj.strR,
        subtitle: obj.strR,
        id: obj.strR,
        description: obj.strR
    },
    components: {
        HintIcon
    },
    data () {
        return {
        }
    },
    methods: {
        clicked() {
            this.$store.dispatch('onboarding/testSuiteSelected', this.id)
        }
    },
    computed: {
        ...mapState('onboarding', ['selectedTestSuite']),
    }
}
</script>

<style lang="sass">
.test-suite-card
    width: 290px
    height: 100px
    border-radius: 8px !important
.selected
    border-color: var(--themeColorDark) !important
    border-width: 3px !important

.test-suite-card
    color: var(--themeColorDark)
    font-size: 22px
    font-weight: 500

.test-suite-subtitle
    color: var(--themeColorDark19)
    font-weight: 500
    font-size: 16px

.test-suite-title-box
    display: flex
    align-items: center
    justify-content: space-between
    margin-bottom: 16px
</style>