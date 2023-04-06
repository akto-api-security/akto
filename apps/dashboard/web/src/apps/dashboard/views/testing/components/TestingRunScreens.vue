<template>
    <div>{{ item }}</div>
</template>

<script>
import obj from '@/util/obj'

export default{
    name: "TestingRunScreens",
    props:{
        item:obj.objR
    }
}
</script>