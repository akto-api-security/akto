<template>

    <spinner v-if="loading" />
    <div v-else class="lb_dropdown">
        <div v-if="isLocalDeploy">
            <div>
                Use generic traffic collector to send traffic to Akto.
                <div v-if="!isLocalDeploy"><a  class="clickable-docs" _target="blank" href="https://docs.akto.io/">Know more</a></div>
            </div>
            <banner-horizontal class="mt-3">
                <div slot="content">
                <div>Akto container config can duplicate your container-traffic and send to Akto dashboard. Go to <a class="clickable-docs" target="blank" href="https://docs.akto.io/">docs</a>.</div>
                </div>
            </banner-horizontal>
        </div>
        <div v-else>
            <div v-if="hasRequiredAccess">
                <v-btn primary dark color="var(--themeColor)" @click="createKubernetesStack" 
                    class="ml-3" 
                    v-if="showSetupMirroringForKubernetesButton && !createStackClicked"
                >
                    Setup traffic processors
                </v-btn>
                
                <div class="text_msg mt-3" v-html="text_msg"></div>
                <div v-if="progressBar.show">
                    <div class="d-flex">
                        <v-progress-linear class="mt-2" background-color="var(--rgbaColor13)" color="var(--rgbaColor7)"
                            :value="progressBar.value">
                        </v-progress-linear>
                        <div class="ml-2">{{ progressBar.value }}%</div>
                    </div>
                </div>
                <div v-if="stackStatus === 'CREATE_COMPLETE'">
                    <div class="steps">Add traffic sources from our docs. Click <a class="clickable-docs" href="/dashboard/observe/inventory">here</a></div>
                    <code-block :lines="yaml" onCopyBtnClickText="Copied to clipboard"></code-block>
                </div>
            </div>
            <div v-else>
                <div class="steps">Use this for AWS Fargate, AWS ECS, TCP-collector, Docker, Docker-compose. Your dashboard's instance needs relevant access to setup traffic processors, please
                    do the
                    following steps:</div>
                <div class="steps">
                    <b>Step 1</b>: Grab the policy JSON below and navigate to Akto Dashboard's current role by clicking <a target="_blank" class="clickable-docs" :href="getAktoDashboardRoleUpdateUrl()">here</a>
                    <code-block :lines="arr" onCopyBtnClickText="Policy copied to clipboard"></code-block>
                </div>
                <div class="steps">
                    <b>Step 2</b>: We will create an inline policy, navigate to JSON tab and paste the copied JSON here.
                </div>
                <div class="steps">
                    <b>Step 3</b>: Click on 'Review policy'.
                </div>
                <div class="steps">
                    <b>Step 4</b>: Now lets name the policy as 'AktoDashboardPolicy'.
                </div>
                <div class="steps">
                    <b>Step 5</b>: Finally create the policy by clicking on 'Create policy'.
                </div>
                <div class="steps">
                    <b>Step 6</b>: Click <a class="clickable-docs" href="/dashboard/quick-start">here</a> to refresh.
                </div>
            </div>
        </div>
    </div>

</template>


<script>
import api from '../api.js'
import CodeBlock from '@/apps/dashboard/shared/components/CodeBlock'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import BannerHorizontal from '../../../shared/components/BannerHorizontal.vue'
export default {
    name: 'Fargate',
    components: {
        CodeBlock,
        Spinner,
        BannerHorizontal
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
            initialLBCount: 0,
            text_msg: null,
            arr: [],
            quick_start_policy_lines_kubernetes: [
                `{`,
                `    "Version": "2012-10-17",`,
                `    "Statement": [`,
                `        {`,
                `            "Sid": "1",`,
                `            "Effect": "Allow",`,
                `            "Action": [`,
                `                "autoscaling:DescribePolicies",`,
                `                "autoscaling:DescribeAutoScalingGroups",`,
                `                "autoscaling:DescribeScalingActivities",`,
                `                "autoscaling:DescribeLaunchConfigurations",`,
                `                "ec2:DescribeSubnets",`,
                `                "ec2:DescribeKeyPairs",`,
                `                "cloudformation:DescribeStacks",`,
                `                "cloudformation:ListStacks",`,
                `                "ec2:DescribeSecurityGroups"`,
                `            ],`,
                `            "Resource": "*"`,
                `        },`,
                `        {`,
                `            "Sid": "2",`,
                `            "Effect": "Allow",`,
                `            "Action": [`,
                `                "autoscaling:PutScalingPolicy",`,
                `                "autoscaling:UpdateAutoScalingGroup",`,
                `                "autoscaling:CreateAutoScalingGroup",`,
                `                "autoscaling:StartInstanceRefresh"`,
                `            ],`,
                `            "Resource": [`,
                `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/*akto*",`,
                `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/*akto*"`,
                `            ]`,
                `        },`,
                `        {`,
                `            "Sid": "3",`,
                `            "Effect": "Allow",`,
                `            "Action": [`,
                `                "autoscaling:CreateLaunchConfiguration"`,
                `            ],`,
                `            "Resource": [`,
                `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/*akto*",`,
                `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/*akto*"`,
                `             ]`,
                `        },`,
                `        {`,
                `            "Sid": "4",`,
                `            "Effect": "Allow",`,
                `            "Action": [`,
                `                "cloudformation:CreateStack",`,
                `                "cloudformation:DescribeStackResources"`,
                `            ],`,
                `            "Resource": [`,
                `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/*akto*/*",`,
                `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/*akto*/*"`,
                `            ]`,
                `        },`,
                `        {`,
                `            "Sid": "5",`,
                `            "Effect": "Allow",`,
                `            "Action": [`,
                `               "ec2:CreateTags"`,
                `            ],`,
                `            "Resource": [`,
                `                "*"`,
                `            ]`,
                `        },`,
                `        {`,
                `            "Sid": "6",`,
                `            "Effect": "Allow",`,
                `            "Action": [`,
                `                "ec2:CreateSecurityGroup",`,
                `                "ec2:CreateTags"`,
                `            ],`,
                `            "Resource": [`,
                `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*",`,
                `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*",`,
                `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:vpc/*"`,
                `            ]`,
                `        },`,
                `        {`,
                `            "Sid": "7", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "ec2:RevokeSecurityGroupEgress", `,
                `                "ec2:AuthorizeSecurityGroupEgress", `,
                `                "ec2:RevokeSecurityGroupIngress", `,
                `                "ec2:AuthorizeSecurityGroupIngress", `,
                `                "ec2:CreateTags"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*", `,
                `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "10", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "elasticloadbalancing:DescribeLoadBalancers", `,
                `                "elasticloadbalancing:DescribeListeners", `,
                `                "elasticloadbalancing:DescribeTargetGroups", `,
                `                "ec2:DescribeVpcs"`,
                `            ], `,
                `            "Resource": `,
                `                "*"`,
                `        }, `,
                `        {`,
                `            "Sid": "11", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "elasticloadbalancing:CreateListener", `,
                `                "elasticloadbalancing:ModifyLoadBalancerAttributes", `,
                `                "elasticloadbalancing:AddTags", `,
                `                "elasticloadbalancing:CreateLoadBalancer"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:loadbalancer/net/*akto*/*"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "12", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "elasticloadbalancing:AddTags", `,
                `                "elasticloadbalancing:CreateTargetGroup"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*", `,
                `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "13", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "lambda:GetFunction", `,
                `                "lambda:CreateFunction", `,
                `                "lambda:DeleteFunction", `,
                `                "lambda:UpdateFunctionConfiguration", `,
                `                "lambda:UpdateFunctionCode", `,
                `                "lambda:GetFunctionCodeSigningConfig", `,
                `                "lambda:InvokeFunction", `,
                `                "lambda:ListFunctions", `,
                `                "lambda:TagResource", `,
                `                "lambda:AddPermission"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*akto*"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "14", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "lambda:ListFunctions" `,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "15", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "logs:CreateLogStream", `,
                `                "logs:PutRetentionPolicy", `,
                `                "logs:TagResource", `,
                `                "logs:CreateLogGroup"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/*akto*", `,
                `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/*akto*:log-stream:"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "16", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "iam:CreateRole", `,
                `                "iam:PutRolePolicy", `,
                `                "iam:GetRole", `,
                `                "iam:GetRolePolicy", `,
                `                "iam:TagRole", `,
                `                "iam:PassRole"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:iam::AWS_ACCOUNT_ID:role/service-role/*akto*", `,
                `                "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "17", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "iam:CreateInstanceProfile", `,
                `                "iam:GetInstanceProfile"`,
                `            ], `,
                `            "Resource": "arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/*akto*"`,
                `        }, `,
                `        {`,
                `            "Sid": "18", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "iam:AddRoleToInstanceProfile"`,
                `            ], `,
                `            "Resource": ["arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/*akto*", "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"]`,
                `        }, `,
                `        {`,
                `            "Sid": "19", `,
                `            "Effect": "Allow", `,
                `            "Action": "s3:GetObject", `,
                `            "Resource": [`,
                `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/get-akto-setup-details.zip", `,
                `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/create-mirror-session.zip", `,
                `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/configure_security_groups.zip", `,
                `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/mirroring-collections-split.zip"`,
                `            ]`,
                `        }, `,
                `        {`,
                `            "Sid": "20", `,
                `            "Effect": "Allow", `,
                `            "Action": [`,
                `                "events:DescribeRule", `,
                `                "events:PutRule", `,
                `                "events:PutTargets"`,
                `            ], `,
                `            "Resource": [`,
                `                "arn:aws:events:AWS_REGION:AWS_ACCOUNT_ID:rule/*akto*"`,
                `            ]`,
                `        } `,
                `    ]`,
                `}`
            ],
            yaml:[
            `        Here are your vars`,
            `            "AKTO_NLB": "<AKTO_NLB_IP>",`,
            `            "AKTO_MONGO_IP": "<AKTO_MONGO_CONN>"`   
            ],
            aktoDashboardRoleName: null,
            isLocalDeploy: false,
            deploymentMethod: "FARGATE",
            stackStatus: "",
            createStackClicked: false
        }
    },
    mounted() {
        this.fetchLBs();
    },
    methods: {
        getAktoDashboardRoleUpdateUrl(){
            return "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + this.aktoDashboardRoleName  + "$createPolicy?step=edit";
        },
        fetchLBs() {
            if((window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy') || (window.CLOUD_TYPE && window.CLOUD_TYPE.toLowerCase() === 'gcp')){
                this.loading = false;
                this.isLocalDeploy = true;
            } else {
                api.fetchLBs({deploymentMethod: this.deploymentMethod}).then((resp) => {
                    if (!resp.dashboardHasNecessaryRole) {
                        this.arr = this.quick_start_policy_lines_kubernetes;
                        for (let i = 0; i < this.arr.length; i++) {
                            let line = this.arr[i];
                            line = line.replaceAll('AWS_REGION', resp.awsRegion);
                            line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
                            this.arr[i] = line;
                        }
                    }
                    this.hasRequiredAccess = resp.dashboardHasNecessaryRole
                    this.aktoDashboardRoleName = resp.aktoDashboardRoleName;
                    this.checkStackState()
                })
            }
        },
        checkStackState() {
            let intervalId = null;
            intervalId = setInterval(async () => {
                api.fetchStackCreationStatus({deploymentMethod: this.deploymentMethod}).then((resp) => {
                    if (this.initialCall) {
                        this.initialCall = false;
                        this.loading = false;
                    }
                    this.stackStatus = resp.stackState.status;
                    this.handleStackState(resp.stackState, intervalId)
                    if(resp.aktoNLBIp && resp.aktoMongoConn){
                        for(let i=0; i<this.yaml.length; i++){
                            let line = this.yaml[i];
                            line = line.replace('<AKTO_NLB_IP>', resp.aktoNLBIp);
                            line = line.replace('<AKTO_MONGO_CONN>', resp.aktoMongoConn);
                            this.yaml[i] = line;
                        }
                    }
                }
                )
            }, 5000)
        },
        handleStackState(stackState, intervalId) {
            if (stackState.status == 'CREATE_IN_PROGRESS') {
                this.renderProgressBar(stackState.creationTime)
                this.text_msg = 'We are setting up data processors stack for you! Grab a cup of coffee, sit back and relax while we work our magic!';
            }
            else if (stackState.status == 'CREATE_COMPLETE') {
                this.removeProgressBarAndStatuschecks(intervalId);
                // this.text_msg = 'Akto is tirelessly processing mirrored traffic to protect your APIs. Click <a class="clickable-docs" href="/dashboard/observe/inventory">here</a> to navigate to API Inventory.';
            }
            else if (stackState.status == 'DOES_NOT_EXISTS') {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Use this for AWS Fargate, AWS ECS, TCP-collector, Docker, Docker-compose. Data processors stack is not setup currently';
            }
            else if (stackState.status == 'CREATION_FAILED') {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Current deployment is getting deleted, please refresh this page in sometime.';
            }
            else if (stackState.status == 'TEMP_DISABLE') {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Current deployment is in progress, please refresh this page in sometime.';
            } else {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Something went wrong while setting up data processors stack, please write to us at support@akto.io'
            }
        },
        renderProgressBar(creationTimeInMs) {
            this.progressBar.show = true;
            const currTimeInMs = Date.now();
            const maxDeploymentTimeInMs = this.progressBar.max_deployment_time_in_ms;
            let progressPercent = (currTimeInMs - creationTimeInMs) / maxDeploymentTimeInMs * 100;
            if (progressPercent > 90) {
                progressPercent = 90;
            }
            // to add more else if blocks to handle cases where deployment is stuck
            this.progressBar.value = Math.round(progressPercent);
        },
        removeProgressBarAndStatuschecks(intervalId) {
            this.progressBar.show = false;
            this.progressBar.value = 0;
            clearInterval(intervalId);
        },
        createKubernetesStack(){
            this.createStackClicked = true
            this.text_msg = "Starting deployment!!!";
            api.createRuntimeStack(this.deploymentMethod).then((resp) => {
                this.checkStackState();
            })
        }
    },
    computed: {
        showSetupMirroringForKubernetesButton(){
            let status = !(this.stackStatus === 'CREATE_COMPLETE' || this.stackStatus === 'CREATE_IN_PROGRESS'
             || this.stackStatus === 'CREATION_FAILED');
            return status;
        },
        disableKubernetesButton(){
            return this.stackStatus === 'CREATE_COMPLETE' || this.stackStatus === 'CREATE_IN_PROGRESS';
        }
    }
}
</script>


<style scoped>
.disabled_lb{
    pointer-events: none;
    opacity: 0.5;
}

.steps{
    margin-top: 6px;
}

.clickable-docs{
    cursor: pointer;
    color: var(--quickStartTheme) !important;
    text-decoration: underline;
}

.text_msg >>> .clickable-docs{
    cursor: pointer;
    color: var(--quickStartTheme) !important;
    text-decoration: underline;
} 
</style>
