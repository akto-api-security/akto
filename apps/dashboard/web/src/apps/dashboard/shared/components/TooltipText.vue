<template>
    <v-tooltip bottom>
        <template v-slot:activator="{ on, attrs }">
            <span v-bind="attrs" v-on="on" :class="['text',type ? 'key ml-1' : 'value ml-4']">
                {{ text }}
            </span>
        </template>
        {{ text }}
    </v-tooltip>
</template>

<script>
import obj from '@/util/obj'

export default {
    name: "TooltipText",
    props: {
        text: obj.strR,
        type: obj.strN,
    },
}
</script>

<style lang="css" scoped>
    .text{
        text-overflow: ellipsis;
        word-break: break-all;
        white-space: nowrap;
        overflow: hidden;
    }
    .key{
        font-weight: 500;
        max-width: 125px;
    }
    .value{
        max-width: 400px;
    }

</style>