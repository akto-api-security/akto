<template>
    <div class="default-info">         
        Only default collection supported for now
    </div>        
</template>

<script>
export default {
    name: "ApiCollections",
    data() {
        return { 
            apiCollectionId: 0
        }
    },
    mounted () {
        
        this.$emit("selected", {collectionName: 'Main', apiCollectionId: this.apiCollectionId})
    }
}
</script>

<style lang="sass" scoped>
    .default-info
        color: #47466A
        font-size: 12px
        margin-top: 16px
</style>