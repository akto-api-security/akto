<template>
    <div class="group-avatars-container">
        <owner-name
            v-for="(item, index) in users.slice(0, 5)"
            :key="index"
            :owner-id="item.id"
            :owner-name="item.name"
            :show-name="false"
            :style="avatarPosition(index)"
        />
    </div>
</template>

<script>
    import OwnerName from "./OwnerName";
    import obj from "@/util/obj";
    export default {
        name: "GroupAvatars",
        components: {
            OwnerName
        },
        props: {
            users: obj.arrR,
            maxAvatars: obj.numR
        },
        methods: {
            avatarPosition(index) {
                return {
                    position: 'absolute',
                    right: ((index+1)*12) + 'px'
                }
            }
        }
    }
</script>

<style scoped lang="sass">
.group-avatars-container
    position: relative


</style>