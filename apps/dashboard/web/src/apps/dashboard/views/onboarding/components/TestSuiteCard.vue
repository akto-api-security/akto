<template>
    <v-card :outlined="true" class="test-suite-card pa-3" @click="clicked" :class="{'selected': (selectedTestSuite === id)}">
        <div style="display: flex; align-items: center;  justify-content: space-between; margin-bottom: 16px;">
            <div style="color: #47466A; font-size: 22px; font-weight: 500">
                {{ title }}
            </div>
            <hint-icon :value="description"/>
        </div>
        <div style="color: #474667; font-weight: 500; font-size: 16px;">
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
    border-color: #47466A !important
    border-width: 3px !important
</style>