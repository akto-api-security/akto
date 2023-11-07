<template>
    <v-row  class="mx-0 pa-0" :style="{ 'margin-top': '10px', 'margin-bottom': '10px' }">
    <v-col cols="2" class="ma-0 pa-0">
        <span class="description-content">{{ title }}</span>
    </v-col>
    <v-col class="my-0 mr-0 ml-7 pa-0">
        <v-chip :style="{ 'height': '24px !important' }" color="var(--themeColorDark6)" class="issue-summary mr-2"
            text-color="var(--white)" :key="index" @click="$emit('tagClick', chipItem)"
            v-for="(chipItem, index) in tagList">
            {{ chipItem }}
        </v-chip>
    </v-col>
    </v-row>
</template>

<script>
import obj from "@/util/obj";
export default {
    name: "TagComponent",
    props: {
        title: obj.strR,
        tagList: obj.arrR
    }
}
</script>