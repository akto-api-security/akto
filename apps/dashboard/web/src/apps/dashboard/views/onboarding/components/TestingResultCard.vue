<template>
    <div class="test-summary-card">
        <div class="test-summary-card-details">
            <div class="d-flex">
                <div class="method">
                    {{ method }}
                </div>
                <div class="path">
                    {{ path }}
                </div>
            </div>
            <div class="severity">
                {{ severity }}
            </div>
        </div>
        <div class="vulnerability-div">
            <div class="vulnerability">
                {{ vulnerability }}
                <hint-icon :value="testName"/>
            </div>
        </div>
    </div>
</template>


<script>
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import obj from "@/util/obj";
import HintIcon from "./HintIcon";

export default {
    name: "TestingResultCard",
    components: {
        Spinner,
        HintIcon
    },
    props: {
        method: obj.strR,
        path: obj.strR,
        severity: obj.strR,
        vulnerability: obj.strR,
        testName: obj.strR,
    },
    data () {
        return {

        }
    },
    methods: {
    },
    mounted() {
    },
    computed: {
    }
}
</script>

<style lang="sass">
.test-summary-card
    display: flex
    flex-direction: column
    align-items: flex-start
    padding: 24px
    gap: 16px
    height: 105px
    background: #FAFAFA
    border: 1px solid #E9E9E9
    border-radius: 12px

.test-summary-card-details
    display: flex
    justify-content: space-between
    width: 100%

.method
    font-weight: 500
    font-size: 16px
    color: #798192
    padding: 0px 8px 0px 0px

.path
    font-weight: 500
    font-size: 16px
    color: #424242

.severity
    background: #FEF3F2
    border-radius: 8px
    font-weight: 500
    font-size: 12px
    text-align: center
    color: #B42318
    width: 44px
    height: 26px
    line-height: 26px

.vulnerability-div
    display: flex
    justify-content: space-between
    width: 100%

.vulnerability
    font-weight: 400
    font-size: 14px
    text-transform: capitalize
    color: #424242
    display: flex
    line-height: 24px

.circular-separator
    border-radius: 50%
    width: 5px
    height: 5px
    background: #616161

.testName
    font-weight: 400
    font-size: 14px
    text-transform: lowercase
    color: #424242

.circular-separator-outer-div
    height: 100%
    justify-content: center
    align-items: center
    display: flex 
</style>