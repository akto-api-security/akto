<template>
    <div class="sample-data-container">
        <div class="sample-data-title">{{title}}</div>
        <div class="sample-data-line">{{firstLine}}</div>
        <div 
            v-for="value, header, index in headers" :key="index"
            class="sample-data-headers" 
        >
            <span class="sample-data-headers-key">{{header}}</span>
            <span class="sample-data-headers-val">: {{value}}</span>
        </div>
        <div class="sample-data-message">{{data}}</div>
    </div>
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "SampleSingleSide",
    props: {
        title: obj.strR,
        firstLine: obj.strR,
        headers: obj.objR,
        data: obj.strR
    }
}
</script>

<style scoped lang="sass">
.sample-data
    &-container
        background-color: #47466A0D
        font-size: 13px
        padding: 8px
        color: #47466A
        margin: 8px 8px 0 0 
    &-title
        font-weight: 500
        text-transform: uppercase
        padding-bottom: 16px    
    &-line
        padding-bottom: 16px    
    &-headers
        overflow-wrap: anywhere
        &-key
            font-weight: 500  
        &-val
            color: #47466A99
      
    &-message
        padding-top: 16px   
        color: #47466A99 
        overflow-wrap: anywhere

</style>