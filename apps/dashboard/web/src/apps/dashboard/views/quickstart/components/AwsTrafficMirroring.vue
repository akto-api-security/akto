<template>
    <div>
        <load-balancers></load-balancers>
    </div>
</template>

<script>
import LoadBalancers from './LoadBalancers.vue'
export default {
    name: "AwsTrafficMirroring",
    components: {
        LoadBalancers
    }
}
</script>

<style lang="sass" scoped>

</style>