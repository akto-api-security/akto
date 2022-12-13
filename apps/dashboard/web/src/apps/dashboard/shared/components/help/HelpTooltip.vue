<template>
    <div style="margin: auto">
        <v-tooltip bottom max-width="200px" z-index="10000">
            <template v-slot:activator="{ on, attrs }">
                <v-icon color="primary" dark v-bind="attrs" v-on="on" :size="size">
                    $far_question-circle
                </v-icon>
            </template>
            <span>{{text}}</span>
        </v-tooltip>
    </div>
</template>

<script>
import obj from '@/util/obj'

export default {
    name: "HelpTooltip",
    props: {
        text: obj.strR,
        size: obj.numR
    }
}
</script>