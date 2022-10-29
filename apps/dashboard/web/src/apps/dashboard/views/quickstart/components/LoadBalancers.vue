<template>

    <spinner v-if="loading" />
    <div v-else class="d-flex lb_dropdown">
        <div v-if="hasRequiredAccess">
            <div class="d-flex">
                <v-select v-model="selectedLBs" :items="availableLBs" item-text="resourceName" item-value="resourceId"
                    label="Select LB(s)" return-object multiple>
                    <template v-slot:selection="{ item, index }">
                        <v-chip v-if="index === 0">
                            <span>{{ item.resourceName }}</span>
                        </v-chip>
                        <span v-if="index === 1" class="grey--text text-caption">
                            (+{{ selectedLBs.length - 1 }} others)
                        </span>
                    </template>
                </v-select>
                <v-btn primary dark color="#6200EA" @click="saveLBs" :disabled="progressBar.show" class="mt-3 ml-3">
                    Update LBs
                </v-btn>
            </div>
            <div>{{text_msg}}</div>
            <div v-if="progressBar.show">
                <div class="d-flex">
                    <v-progress-linear class="mt-2" background-color="rgba(98, 0, 234,0.2)" color="rgb(98, 0, 234)"
                        :value="progressBar.value">
                    </v-progress-linear>
                    <div class="ml-2">{{progressBar.value}}%</div>
                </div>
                <!-- <div>
                    We are setting up mirroring for you! Grab a cup of coffee, sit back and relax while we work our
                    magic!
                </div> -->
            </div>
        </div>
        <div v-else>
            <div class="steps">Your dashboard's instance needs relevant access to setup traffic mirroring, please
                execute the
                following commands:</div>
            <div class="steps">
                <b>Step 1</b>: Open aws console and navigate to IAM > Roles
            </div>
            <div class="steps">
                <b>Step 2</b>: Locate and open AktoDashboardRole
            </div>
            <div class="steps">
                <b>Step 3</b>: Click on 'Add permissions' and then on 'Create inline policy'
            </div>
            <div class="steps">
                <b>Step 4</b>: Navigate to 'JSON' tab, paste the following json there
                <code-block :lines="quick_start_policy_lines"></code-block>
            </div>
        </div>
    </div>

</template>


<script>
import api from '../api.js'
import CodeBlock from '@/apps/dashboard/shared/components/CodeBlock'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
export default {
    name: 'LoadBalancers',
    components: {
        CodeBlock,
        Spinner,
    },
    data() {
        return {
            initialCall: true,
            loading: true,
            hasRequiredAccess: false,
            availableLBs: [],
            selectedLBs: [],
            existingSelectedLBs: [],
            stackCreationStartTime: null,
            progressBar: {
                show: false,
                value: 0,
                max_deployment_time_in_ms: 8 * 60 * 1000,
            },
            text_msg: null,
            quick_start_policy_lines: [
                `{`,
                `   "Version": "2012-10-17",`,
                `   "Statement": [`,
                `       {`,
                `           "Sid": "VisualEditor0",`,
                `           "Effect": "Allow",`,
                `           "Action": [`,
                `                "elasticloadbalancing:*TargetGroups",`,
                `                "ec2:AuthorizeSecurityGroupIngress",`,
                `                "autoscaling:Describe*",`,
                `                "iam:*InstanceProfile*",`,
                `                "iam:CreateRole",`,
                `                "ec2:*TrafficMirror*",`,
                `                "ec2:DeleteTrafficMirrorTarget",`,
                `                "iam:PutRolePolicy",`,
                `                "elasticloadbalancing:DeleteLoadBalancer",`,
                `                "elasticloadbalancing:DescribeLoadBalancers",`,
                `                "iam:PassRole",`,
                `                "ec2:*SecurityGroup",`,
                `                "elasticloadbalancing:Describe",`,
                `                "iam:DeleteRolePolicy",`,
                `                "ec2:RevokeSecurityGroupEgress",`,
                `                "autoscaling:Delete*",`,
                `                "autoscaling:PutScalingPolicy",`,
                `                "events:*",`,
                `                "elasticloadbalancing:*TargetGroup",`,
                `                "ec2:DeleteTrafficMirrorFilterRule",`,
                `                "ec2:DescribeKeyPairs",`,
                `                "autoscaling:*AutoScalingGroup*",`,
                `                "iam:GetRole",`,
                `                "ec2:AuthorizeSecurityGroupEgress",`,
                `                "ec2:DeleteTrafficMirrorFilter",`,
                `                "events:*Rule",`,
                `                "elasticloadbalancing:CreateLoadBalancer",`,
                `                "autoscaling:*LaunchConfiguration*",`,
                `                "ec2:CreateTags",`,
                `                "lambda:GetFunction",`,
                `                "elasticloadbalancing:*Listeners",`,
                `                "cloudformation:DescribeStackResources",`,
                `                "lambda:UpdateFunctionConfiguration",`,
                `                "iam:DeleteRole",`,
                `                "elasticloadbalancing:Delete*",`,
                `                "ec2:*SecurityGroups",`,
                `                "ec2:DeleteTrafficMirrorSession",`,
                `                "cloudformation:DescribeStacks",`,
                `                "elasticloadbalancing:*Listener",`,
                `                "iam:Delete*",`,
                `                "s3:GetObject",`,
                `                "cloudformation:CreateStack",`,
                `                "ec2:DescribeVpcs",`,
                `                "lambda:*Function",`,
                `                "ec2:DescribeSubnets",`,
                `                "ec2:Delete*",`,
                `                "iam:GetRolePolicy",`,
                `                "elasticloadbalancing:ModifyLoadBalancerAttributes",`,
                `                "logs:*LogGroup",`,
                `                "logs:*RetentionPolicy",`,
                `                "lambda:AddPermission"`,
                `            ],`,
                `            "Resource": "*"`,
                `        }`,
                `    ]`,
                `}`
            ],
        }
    },
    mounted() {
        this.fetchLBs();
    },
    methods: {
        fetchLBs() {
            api.fetchLBs().then((resp) => {
                console.log(resp)
                this.hasRequiredAccess = resp.dashboardHasNecessaryRole
                this.availableLBs = resp.availableLBs;
                this.selectedLBs = resp.selectedLBs;
                this.existingSelectedLBs = resp.selectedLBs;
                this.checkStackState()
            })
        },
        saveLBs() {
            api.saveLBs(this.selectedLBs).then((resp) => {
                this.availableLBs = resp.availableLBs;
                this.selectedLBs = resp.selectedLBs;
                this.existingSelectedLBs = resp.selectedLBs;
                console.log(resp);
                if (resp.isFirstSetup) {
                    this.checkStackState()
                    mixpanel.track("mirroring_stack_creation_initialized");
                } else {
                    mixpanel.track("loadbalancers_updated");
                }
            })
        },
        checkStackState() {
            let intervalId = null;
            intervalId = setInterval(async () => {
                console.log('Tracking stack creation status....')
                api.fetchStackCreationStatus().then((resp) => {
                    if (this.initialCall) {
                        this.initialCall = false;
                        this.loading = false;
                    }
                    this.handleStackState(resp.stackState, intervalId)
                }
                )
            }, 5000)
        },
        handleStackState(stackState, intervalId) {
            if (stackState.status == 'CREATE_IN_PROGRESS') {
                this.renderProgressBar(stackState.creationTime)
                console.log("Stack is being created");
                this.text_msg = 'We are setting up mirroring for you! Grab a cup of coffee, sit back and relax while we work our magic!';
            }
            else if (stackState.status == 'CREATE_COMPLETE') {
                console.log("Stack created successfully, stopping further calls to status api");
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Akto is tirelessly processing mirrored traffic to protect your APIs';
            }
            else if (stackState.status == 'DOES_NOT_EXISTS') {
                console.log(`Stack doesn't exist, removing calls to status api`);
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Mirroring is not setup currently, choose 1 or more LBs to enable mirroring';
            } else {
                console.log('Something went wrong here, removing calls to status api');
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Something went wrong while setting up mirroring, please write to us at support@akto.io'
            }
        },
        renderProgressBar(creationTimeInMs) {
            this.progressBar.show = true;
            const currTimeInMs = Date.now();
            const maxDeploymentTimeInMs = this.progressBar.max_deployment_time_in_ms;
            console.log("currTime:" + currTimeInMs + "; creationTime: " + creationTimeInMs + "; maxDeploymentTime:" + maxDeploymentTimeInMs)
            let progressPercent = (currTimeInMs - creationTimeInMs) / maxDeploymentTimeInMs * 100;
            console.log("Actual pp:" + progressPercent);
            if (progressPercent > 90) {
                progressPercent = 90;
            }
            // to add more else if blocks to handle cases where deployment is stuck
            console.log("Updated pp:" + progressPercent);
            this.progressBar.value = Math.round(progressPercent);
        },
        removeProgressBarAndStatuschecks(intervalId) {
            this.progressBar.show = false;
            this.progressBar.value = 0;
            clearInterval(intervalId);
        }
    }
}
</script>


<style lang="sass" scoped>
.lb_dropdown
    min-width: 500px
    .v-select__selections
        padding: 0px !important     
    .v-input__slot
        min-height: 32px !important

.steps
    margin-top: 4px
</style>
