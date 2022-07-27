<template>
    <div>
        <keep-alive include="ApiChanges,SensitiveData">
            <router-view/>
        </keep-alive>
    </div>    
</template>

<script>
export default {
    name: "Observe"
}
</script>

<style lang="sass" scoped>

</style>