<template>
    <v-card :outlined="true" class="test-suite-card pa-3">
        <div style="display: flex; align-items: center;  justify-content: space-between; margin-bottom: 16px;">
            <div style="color: #47466A; font-size: 22px; font-weight: 500">
                {{ title }}
            </div>
            <div>
                <v-icon>$far_question-circle</v-icon>
            </div>
        </div>
        <div style="color: #474667; font-weight: 500; font-size: 16px;">
            {{ subtitle }}
        </div>
    </v-card>
</template>

<script>
import obj from "@/util/obj";

export default {
    name: "TestSuiteCard",
    props: {
        title: obj.strR,
        subtitle: obj.strR
    },
    data () {
        return {
        }
    },
}
</script>

<style lang="sass">
.test-suite-card
    width: 290px
    height: 100px
    border-radius: 8px !important
</style>