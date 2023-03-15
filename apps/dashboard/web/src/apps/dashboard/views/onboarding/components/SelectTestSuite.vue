<template>
    <div class="test-suites">
        <div v-if="this.testSuitesArr">
            <div class="test-suite-card-container" v-for="(arr,idx1) in this.testSuitesArr">
                <test-suite-card 
                    v-for="(testSuite, idx2) in arr" 
                    :key="idx2"
                    :title="testSuite['name']" 
                    :subtitle="generateSubtitle(testSuite['tests'].length)"
                    :id="testSuite['_name']"
                    :description="testSuite['description']"
                />
            </div>
        </div>
        <div v-else class="spinner-div">
            <spinner :size="50"/>
        </div>
    </div>
</template>

<script>

import TestSuiteCard from '@/apps/dashboard/views/onboarding/components/TestSuiteCard'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import {mapState} from 'vuex'

export default {
    name: "SelectTestSuite",
    components: {
        TestSuiteCard,
        Spinner
    },
    data () {
        return {
        }
    },
    methods: {
        generateSubtitle(num) {
            return num + " tests"
        }
    },

    computed: {
        ...mapState('onboarding', ['testSuites']),
        testSuitesArr () {
            let newArr = []
            let arr = this.testSuites.map(a => ({...a}))
            while(arr.length) newArr.push(arr.splice(0,2));
            return newArr
        }
    }

}
</script>

<style lang="sass">
.test-suite-card-container
    display: flex
    padding: 20px 20px 5px 20px
    justify-content: space-between

.spinner-div
    display: flex
    justify-content: center
    height: 100%
    align-items: center

.test-suites
    width: 650px
    height: 220px
    margin-bottom: 36px

</style>