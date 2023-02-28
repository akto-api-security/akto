<template>
    <div style="width: 650px;height: 220px; margin-bottom: 36px;">
        <div class="test-suite-card-container">
            <test-suite-card title="Business Logic Scan" subtitle="10 tests"/>
            <test-suite-card title="Passive Scan" subtitle="30 tests"/>
        </div>
        <div class="test-suite-card-container">
            <test-suite-card title="Deep Scan" subtitle="10 tests"/>
        </div>
    </div>
</template>

<script>

import TestSuiteCard from '@/apps/dashboard/views/onboarding/components/TestSuiteCard'

export default {
    name: "SelectTestSuite",
    components: {
        TestSuiteCard
    },
    data () {
        return {
        }
    },
}
</script>

<style lang="sass">
.test-suite-card-container
    display: flex
    padding: 20px 20px 5px 20px
    justify-content: space-between

</style>