<template>
    <div >
        <div class="issue-box">
            <div>{{categoryName}}</div>
            <div>{{categoryDescription}}</div>
            <div>{{method}}</div>
            <div>{{endpoint}}</div>
            <div>{{collectionName}}</div>
            <div>{{creationTime}}</div>
        </div>
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
        severity : obj.strR,
        collectionName : obj.strR,
        categoryName : obj.strR,
        categoryDescription : obj.strR,
        testType: obj.strR
    }
}

</script>

<style scoped >
.issue-box {
    box-sizing: border-box;
    background: #FCFCFD;
    border: 1px solid #D6D3DA;
    border-radius: 6px;
}
    
</style>