<template>
    <div>
        <v-tooltip bottom>
            <template v-slot:activator="{on,attrs}">
                <v-icon v-bind="attrs" v-on="on">$far_question-circle</v-icon>
            </template>
            <div style="max-width: 160px;">
                {{ value }}
            </div>
        </v-tooltip>
    </div>
</template>

<script>
import obj from "@/util/obj";

export default {
    name: "TestSuiteCard",
    props: {
        value : obj.strR
    },
    data () {
        return {
        }
    },
    methods: {

    },
    computed: {
    }
}
</script>

<style lang="sass">
</style>