<template>
    <div class="brdb brdt ma-4 pa-4">
        <div>{{method}}{{endpoint}}</div>
        <div>{{creationTime}}{{severity}}</div>
    </div>
</template>

<script>

import obj from "@/util/obj"
export default {
    name : "IssueBox",
    props : {
        method : obj.strR, 
        endpoint : obj.strR,
        creationTime : obj.numR,
        severity : obj.strR
    }
}

</script>

<style scoped lang='sass'>

</style>